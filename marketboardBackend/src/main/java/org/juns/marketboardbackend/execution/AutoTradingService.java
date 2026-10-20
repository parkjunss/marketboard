package org.juns.marketboardbackend.execution;

import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.juns.marketboardbackend.collector.CollectorClient;
import org.juns.marketboardbackend.common.exception.ResourceNotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class AutoTradingService {
    public record Run(long id, String status, int cursor, int barCount, JsonNode configuration,
                      JsonNode state, Instant createdAt, String error, Long strategyId) {}
    public record Strategy(long id, String name, long sourceRunId, JsonNode configuration, Instant createdAt) {}
    public record RunComparison(Run left, Run right, boolean comparable, List<String> differences,
                                int steps, JsonNode leftResult, JsonNode rightResult) {}
    public record Journal(int step, Instant recordedAt, JsonNode payload) {}
    public record Control(String action, Instant recordedAt) {}
    public record Detail(Run run, List<Journal> journal, List<Control> controls) {}
    public record PriceRange(String ticker, String firstDate, String lastDate, int count) {}
    public record ValidationSummary(long id, long strategyId, LocalDate startDate, LocalDate endDate, Instant createdAt) {}
    public record Validation(ValidationSummary summary, String strategyName, JsonNode configuration,
                             int warmupCount, String warmupStart, String warmupEnd, JsonNode result) {}
    public record CostSummary(long id, long validationId, Instant createdAt) {}
    public record CostAnalysis(CostSummary summary, Validation validation, JsonNode results) {}
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final CollectorClient collector;
    public AutoTradingService(JdbcTemplate jdbc, ObjectMapper mapper, CollectorClient collector) {
        this.jdbc = jdbc; this.mapper = mapper; this.collector = collector;
    }
    private Run row(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
        return new Run(rs.getLong("id"), rs.getString("status"), rs.getInt("cursor_position"),
                rs.getInt("bar_count"), mapper.readTree(rs.getString("configuration_json")),
                mapper.readTree(rs.getString("state_json")), rs.getTimestamp("created_at").toInstant(), rs.getString("last_error"),
                rs.getObject("strategy_id", Long.class));
    }
    private Strategy strategyRow(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
        return new Strategy(rs.getLong("id"), rs.getString("name"), rs.getLong("source_run_id"),
                mapper.readTree(rs.getString("configuration_json")), rs.getTimestamp("created_at").toInstant());
    }
    private Strategy ownedStrategy(long owner, long id) {
        var rows = jdbc.query("SELECT * FROM auto_trading_strategies WHERE user_id=? AND id=?", this::strategyRow, owner, id);
        if (rows.isEmpty()) throw new ResourceNotFoundException("저장된 전략을 찾을 수 없습니다.");
        return rows.get(0);
    }
    public List<Strategy> strategies(long owner) {
        return jdbc.query("SELECT * FROM auto_trading_strategies WHERE user_id=? ORDER BY id DESC", this::strategyRow, owner);
    }
    private ValidationSummary validationSummary(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
        return new ValidationSummary(rs.getLong("id"), rs.getLong("strategy_id"), rs.getDate("start_date").toLocalDate(),
                rs.getDate("end_date").toLocalDate(), rs.getTimestamp("created_at").toInstant());
    }
    public List<ValidationSummary> validations(long owner) {
        return jdbc.query("SELECT * FROM auto_trading_validations WHERE user_id=? ORDER BY id DESC", this::validationSummary, owner);
    }
    private JsonNode validationSnapshot(long owner, long id) {
        var rows = jdbc.query("SELECT snapshot_json FROM auto_trading_validations WHERE user_id=? AND id=?",
                (rs, n) -> mapper.readTree(rs.getString(1)), owner, id);
        if (rows.isEmpty()) throw new ResourceNotFoundException("기간 검증 기록을 찾을 수 없습니다.");
        return rows.get(0);
    }
    private CostSummary costSummary(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
        return new CostSummary(rs.getLong("id"), rs.getLong("validation_id"), rs.getTimestamp("created_at").toInstant());
    }
    public List<CostSummary> costAnalyses(long owner, long validationId) {
        validationSnapshot(owner, validationId);
        return jdbc.query("SELECT * FROM auto_trading_cost_analyses WHERE user_id=? AND validation_id=? ORDER BY id DESC",
                this::costSummary, owner, validationId);
    }
    @Transactional(readOnly=true)
    public CostAnalysis costAnalysis(long owner, long id) {
        var rows = jdbc.query("SELECT * FROM auto_trading_cost_analyses WHERE user_id=? AND id=?", this::costSummary, owner, id);
        if (rows.isEmpty()) throw new ResourceNotFoundException("비용 비교 기록을 찾을 수 없습니다.");
        var summary = rows.get(0);
        return new CostAnalysis(summary, validation(owner, summary.validationId()),
                mapper.readTree(jdbc.queryForObject("SELECT results_json FROM auto_trading_cost_analyses WHERE id=?", String.class, id)));
    }
    @Transactional
    public CostAnalysis analyzeCosts(long owner, String key, long validationId, AutoTradingController.CostRequest request) {
        jdbc.queryForObject("SELECT id FROM users WHERE id=? FOR UPDATE", Long.class, owner);
        var snapshot = validationSnapshot(owner, validationId);
        var requested = mapper.valueToTree(request.scenarios());
        var existing = jdbc.query("SELECT * FROM auto_trading_cost_analyses WHERE user_id=? AND request_key=?", this::costSummary, owner, key);
        if (!existing.isEmpty()) {
            var previous = existing.get(0);
            var stored = mapper.readTree(jdbc.queryForObject("SELECT scenarios_json FROM auto_trading_cost_analyses WHERE id=?", String.class, previous.id()));
            if (previous.validationId() != validationId || !stored.equals(requested))
                throw new IllegalArgumentException("같은 요청 키로 비용 조건을 변경할 수 없습니다.");
            return costAnalysis(owner, previous.id());
        }
        if (request.scenarios().isEmpty() || request.scenarios().size() > 8) throw new IllegalArgumentException("비용 시나리오는 1~8개입니다.");
        if (jdbc.queryForObject("SELECT COUNT(*) FROM auto_trading_cost_analyses WHERE user_id=?", Integer.class, owner) >= 100)
            throw new IllegalArgumentException("비용 비교 기록은 최대 100개입니다.");
        var base = snapshot.path("validationConfiguration");
        var scenarios = new ArrayList<AutoTradingController.CostScenario>();
        scenarios.add(new AutoTradingController.CostScenario(base.path("feeBps").asText(), base.path("slippageBps").asText()));
        scenarios.addAll(request.scenarios());
        var seen = new HashSet<String>();
        var results = mapper.createArrayNode();
        for (var scenario : scenarios) {
            var fee = new java.math.BigDecimal(scenario.feeBps());
            var slip = new java.math.BigDecimal(scenario.slippageBps());
            if (fee.signum() < 0 || slip.signum() < 0 || fee.compareTo(new java.math.BigDecimal("100")) > 0 || slip.compareTo(new java.math.BigDecimal("100")) > 0)
                throw new IllegalArgumentException("수수료와 슬리피지는 각각 0~100bp입니다.");
            if (!seen.add(fee.stripTrailingZeros().toPlainString() + "/" + slip.stripTrailingZeros().toPlainString()))
                throw new IllegalArgumentException("기준 조건과 중복되지 않는 비용 조건을 입력하세요.");
            var config = ((tools.jackson.databind.node.ObjectNode) base).deepCopy();
            config.put("feeBps", scenario.feeBps()); config.put("slippageBps", scenario.slippageBps());
            var calculated = collector.replayAutoTrading(Map.of("configuration", config, "bars", snapshot.path("validationBars"),
                    "warmup", snapshot.path("warmup"), "comparison", true));
            results.add(mapper.valueToTree(Map.of("feeBps", scenario.feeBps(), "slippageBps", scenario.slippageBps(),
                    "baseline", results.isEmpty(), "result", calculated)));
        }
        var holder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var s = connection.prepareStatement("""
                    INSERT INTO auto_trading_cost_analyses(user_id,validation_id,request_key,scenarios_json,results_json,created_at)
                    VALUES(?,?,?,?,?,?)
                    """, java.sql.Statement.RETURN_GENERATED_KEYS);
            s.setLong(1, owner); s.setLong(2, validationId); s.setString(3, key); s.setString(4, mapper.writeValueAsString(requested));
            s.setString(5, mapper.writeValueAsString(results)); s.setTimestamp(6, Timestamp.from(Instant.now())); return s;
        }, holder);
        return costAnalysis(owner, Objects.requireNonNull(holder.getKey()).longValue());
    }
    @Transactional(readOnly=true)
    public Validation validation(long owner, long id) {
        var snapshot = validationSnapshot(owner, id);
        var summary = jdbc.queryForObject("SELECT * FROM auto_trading_validations WHERE id=?", this::validationSummary, id);
        var warmup = snapshot.path("warmup");
        return new Validation(summary, snapshot.path("strategyName").asText(), snapshot.path("configuration"), warmup.size(),
                warmup.get(0).path("date").asText(), warmup.get(warmup.size() - 1).path("date").asText(),
                mapper.readTree(jdbc.queryForObject("SELECT result_json FROM auto_trading_validations WHERE id=?", String.class, id)));
    }
    private JsonNode calculateValidation(JsonNode snapshot) {
        return mapper.valueToTree(Map.of(
                "training", collector.replayAutoTrading(Map.of("configuration", snapshot.path("configuration"),
                        "bars", snapshot.path("trainingBars"), "comparison", true)),
                "validation", collector.replayAutoTrading(Map.of("configuration", snapshot.path("validationConfiguration"),
                        "bars", snapshot.path("validationBars"), "warmup", snapshot.path("warmup"), "comparison", true))));
    }
    @Transactional(readOnly=true)
    public Map<String, Boolean> verifyValidation(long owner, long id) {
        var snapshot = validationSnapshot(owner, id);
        var stored = mapper.readTree(jdbc.queryForObject("SELECT result_json FROM auto_trading_validations WHERE id=?", String.class, id));
        return Map.of("matches", stored.equals(calculateValidation(snapshot)));
    }
    @Transactional
    public Validation validatePeriod(long owner, String key, long strategyId, AutoTradingController.ValidationPeriod period) {
        jdbc.queryForObject("SELECT id FROM users WHERE id=? FOR UPDATE", Long.class, owner);
        var strategy = ownedStrategy(owner, strategyId);
        var existing = jdbc.query("SELECT * FROM auto_trading_validations WHERE user_id=? AND request_key=?", this::validationSummary, owner, key);
        if (!existing.isEmpty()) {
            var previous = existing.get(0);
            if (previous.strategyId() != strategyId || !previous.startDate().equals(period.startDate()) || !previous.endDate().equals(period.endDate()))
                throw new IllegalArgumentException("같은 요청 키로 검증 기간이나 전략을 변경할 수 없습니다.");
            return validation(owner, previous.id());
        }
        var config = strategy.configuration();
        if (!period.startDate().isAfter(LocalDate.parse(config.path("endDate").asText()))
                || period.startDate().isAfter(period.endDate()) || !period.endDate().isBefore(LocalDate.now(ZoneOffset.UTC)))
            throw new IllegalArgumentException("검증 기간은 조정 기간 종료일 이후이며 완료된 과거 날짜여야 합니다.");
        if (jdbc.queryForObject("SELECT COUNT(*) FROM auto_trading_validations WHERE user_id=?", Integer.class, owner) >= 100)
            throw new IllegalArgumentException("기간 검증 기록은 최대 100개입니다.");
        owned(owner, strategy.sourceRunId(), false);
        var validationBars = jdbc.query("""
                SELECT p.ts,p.open,p.close FROM price_history p JOIN symbols s ON s.id=p.symbol_id
                WHERE s.ticker=? AND p.timeframe='1d' AND p.ts>=? AND p.ts<? ORDER BY p.ts LIMIT 1001
                """, this::priceBar, config.path("ticker").asText(),
                Timestamp.from(period.startDate().atStartOfDay().toInstant(ZoneOffset.UTC)),
                Timestamp.from(period.endDate().plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)));
        int window = config.path("window").asInt();
        if (validationBars.isEmpty() || validationBars.size() + window > 1000)
            throw new IllegalArgumentException("검증 가격이 없거나 준비 가격 포함 1,000개를 초과합니다.");
        var warmup = new ArrayList<>(jdbc.query("""
                SELECT p.ts,p.open,p.close FROM price_history p JOIN symbols s ON s.id=p.symbol_id
                WHERE s.ticker=? AND p.timeframe='1d' AND p.ts<? ORDER BY p.ts DESC LIMIT ?
                """, this::priceBar, config.path("ticker").asText(),
                Timestamp.from(period.startDate().atStartOfDay().toInstant(ZoneOffset.UTC)), window));
        if (warmup.size() != window) throw new IllegalArgumentException("검증 시작일 직전의 이동평균 준비 가격이 부족합니다.");
        Collections.reverse(warmup);
        var validationConfig = ((tools.jackson.databind.node.ObjectNode) config).deepCopy();
        validationConfig.put("startDate", period.startDate().toString()).put("endDate", period.endDate().toString());
        var snapshot = mapper.valueToTree(Map.of("strategyName", strategy.name(), "configuration", config,
                "validationConfiguration", validationConfig, "trainingBars", prices(strategy.sourceRunId()),
                "validationBars", validationBars, "warmup", warmup));
        var result = calculateValidation(snapshot);
        var holder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var s = connection.prepareStatement("""
                    INSERT INTO auto_trading_validations(user_id,request_key,strategy_id,start_date,end_date,snapshot_json,result_json,created_at)
                    VALUES(?,?,?,?,?,?,?,?)
                    """, java.sql.Statement.RETURN_GENERATED_KEYS);
            s.setLong(1, owner); s.setString(2, key); s.setLong(3, strategyId);
            s.setDate(4, java.sql.Date.valueOf(period.startDate())); s.setDate(5, java.sql.Date.valueOf(period.endDate()));
            s.setString(6, mapper.writeValueAsString(snapshot)); s.setString(7, mapper.writeValueAsString(result));
            s.setTimestamp(8, Timestamp.from(Instant.now()));
            return s;
        }, holder);
        return validation(owner, Objects.requireNonNull(holder.getKey()).longValue());
    }
    private Map<String, String> priceBar(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
        return Map.of("date", rs.getTimestamp("ts").toInstant().atOffset(ZoneOffset.UTC).toLocalDate().toString(),
                "open", rs.getBigDecimal("open").toPlainString(), "close", rs.getBigDecimal("close").toPlainString());
    }
    private JsonNode prices(long id) {
        return mapper.readTree(jdbc.queryForObject("SELECT prices_json FROM auto_trading_runs WHERE id=?", String.class, id));
    }
    private List<String> conditionDifferences(JsonNode left, JsonNode right) {
        var differences = new ArrayList<String>();
        for (var field : List.of("version", "ticker", "startDate", "endDate", "initialCapital", "feeBps", "slippageBps")) {
            boolean equal = Set.of("initialCapital", "feeBps", "slippageBps").contains(field)
                    ? new java.math.BigDecimal(left.path(field).asText()).compareTo(new java.math.BigDecimal(right.path(field).asText())) == 0
                    : left.path(field).equals(right.path(field));
            if (!equal) differences.add(field);
        }
        return differences;
    }
    @Transactional
    public Strategy saveStrategy(long owner, String key, AutoTradingController.SaveStrategy request) {
        jdbc.queryForObject("SELECT id FROM users WHERE id=? FOR UPDATE", Long.class, owner);
        var source = owned(owner, request.sourceRunId(), false);
        var config = mapper.valueToTree(request.configuration());
        var existing = jdbc.query("SELECT * FROM auto_trading_strategies WHERE user_id=? AND request_key=?", this::strategyRow, owner, key);
        String name = request.name().strip();
        if (name.isEmpty() || name.length() > 80) throw new IllegalArgumentException("전략 이름은 1~80자입니다.");
        if (!existing.isEmpty()) {
            var strategy = existing.get(0);
            if (!strategy.name().equals(name) || strategy.sourceRunId() != source.id() || !strategy.configuration().equals(config))
                throw new IllegalArgumentException("같은 요청 키로 전략을 변경할 수 없습니다.");
            return strategy;
        }
        if (!conditionDifferences(source.configuration(), config).isEmpty())
            throw new IllegalArgumentException("복제 시 종목·기간·자금·비용·전략 버전은 원본과 같아야 합니다.");
        if (jdbc.queryForObject("SELECT COUNT(*) FROM auto_trading_strategies WHERE user_id=?", Integer.class, owner) >= 100)
            throw new IllegalArgumentException("저장 전략은 최대 100개입니다.");
        var bars = prices(source.id());
        if (bars.size() <= request.configuration().window()) throw new IllegalArgumentException("이동평균 기간보다 많은 가격이 필요합니다.");
        collector.replayAutoTrading(Map.of("configuration", config, "bars", bars));
        var holder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var s = connection.prepareStatement("""
                    INSERT INTO auto_trading_strategies(user_id,request_key,name,source_run_id,configuration_json,created_at)
                    VALUES(?,?,?,?,?,?)
                    """, java.sql.Statement.RETURN_GENERATED_KEYS);
            s.setLong(1, owner); s.setString(2, key); s.setString(3, name); s.setLong(4, source.id());
            s.setString(5, mapper.writeValueAsString(config)); s.setTimestamp(6, Timestamp.from(Instant.now()));
            return s;
        }, holder);
        return ownedStrategy(owner, Objects.requireNonNull(holder.getKey()).longValue());
    }
    @Transactional
    public Run runStrategy(long owner, String key, long strategyId) {
        var strategy = ownedStrategy(owner, strategyId);
        owned(owner, strategy.sourceRunId(), false);
        return create(owner, key, mapper.treeToValue(strategy.configuration(), AutoTradingController.Configuration.class),
                prices(strategy.sourceRunId()), strategyId);
    }
    @Transactional(readOnly=true)
    public RunComparison compareRuns(long owner, long leftId, long rightId) {
        var left = owned(owner, leftId, false);
        var right = owned(owner, rightId, false);
        var differences = conditionDifferences(left.configuration(), right.configuration());
        if (!prices(leftId).equals(prices(rightId))) differences.add("prices");
        if (!differences.isEmpty()) return new RunComparison(left, right, false, differences, 0, null, null);
        int steps = Math.min(left.cursor(), right.cursor());
        return new RunComparison(left, right, true, differences, steps, replay(left, steps, true), replay(right, steps, true));
    }
    private Run owned(long owner, long id, boolean lock) {
        var rows = jdbc.query("SELECT * FROM auto_trading_runs WHERE user_id=? AND id=?" + (lock ? " FOR UPDATE" : ""), this::row, owner, id);
        if (rows.isEmpty()) throw new ResourceNotFoundException("자동 모의 실행을 찾을 수 없습니다.");
        return rows.get(0);
    }
    public List<PriceRange> prices() {
        return jdbc.query("""
                SELECT s.ticker,MIN(p.ts) first_ts,MAX(p.ts) last_ts,COUNT(*) n
                FROM symbols s JOIN price_history p ON p.symbol_id=s.id
                WHERE p.timeframe='1d' GROUP BY s.ticker HAVING COUNT(*)>=3 ORDER BY s.ticker
                """, (rs, n) -> new PriceRange(rs.getString("ticker"), rs.getTimestamp("first_ts").toInstant().atOffset(ZoneOffset.UTC).toLocalDate().toString(),
                rs.getTimestamp("last_ts").toInstant().atOffset(ZoneOffset.UTC).toLocalDate().toString(), rs.getInt("n")));
    }
    public List<Run> list(long owner) {
        return jdbc.query("SELECT * FROM auto_trading_runs WHERE user_id=? ORDER BY id DESC LIMIT 100", this::row, owner);
    }
    @Transactional(readOnly=true)
    public Detail detail(long owner, long id) {
        var run = owned(owner, id, false);
        return new Detail(run, journal(id), jdbc.query("SELECT action,recorded_at FROM auto_trading_controls WHERE run_id=? ORDER BY id",
                (rs, n) -> new Control(rs.getString(1), rs.getTimestamp(2).toInstant()), id));
    }
    private List<Journal> journal(long id) {
        return jdbc.query("SELECT * FROM auto_trading_journal WHERE run_id=? ORDER BY step_number",
                (rs, n) -> new Journal(rs.getInt("step_number"), rs.getTimestamp("recorded_at").toInstant(), mapper.readTree(rs.getString("payload_json"))), id);
    }
    private void controlEvent(long id, String action) {
        jdbc.update("INSERT INTO auto_trading_controls(run_id,action,recorded_at) VALUES(?,?,?)", id, action, Timestamp.from(Instant.now()));
    }
    @Transactional
    public Run create(long owner, String key, AutoTradingController.Configuration config) {
        return create(owner, key, config, null, null);
    }
    private Run create(long owner, String key, AutoTradingController.Configuration config, JsonNode frozenPrices, Long strategyId) {
        // Serialize creation per user, including retries of the same request.
        jdbc.queryForObject("SELECT id FROM users WHERE id=? FOR UPDATE", Long.class, owner);
        var existing = jdbc.query("SELECT * FROM auto_trading_runs WHERE user_id=? AND request_key=?", this::row, owner, key);
        JsonNode configuration = mapper.valueToTree(config);
        if (!existing.isEmpty()) {
            if (!existing.get(0).configuration().equals(configuration) || !Objects.equals(existing.get(0).strategyId(), strategyId))
                throw new IllegalArgumentException("같은 요청 키로 설정을 변경할 수 없습니다.");
            return existing.get(0);
        }
        if (config.startDate().isAfter(config.endDate()) || !config.endDate().isBefore(LocalDate.now(ZoneOffset.UTC)))
            throw new IllegalArgumentException("완료된 과거 거래일 범위를 지정하세요.");
        if (jdbc.queryForObject("SELECT COUNT(*) FROM auto_trading_runs WHERE user_id=? AND status IN ('RUNNING','PAUSED','ERROR')", Integer.class, owner) >= 5)
            throw new IllegalArgumentException("미완료 실행은 최대 5개입니다.");
        JsonNode bars = frozenPrices != null ? frozenPrices : mapper.valueToTree(jdbc.query("""
                SELECT p.ts,p.open,p.close FROM price_history p JOIN symbols s ON s.id=p.symbol_id
                WHERE s.ticker=? AND p.timeframe='1d' AND p.ts>=? AND p.ts<? ORDER BY p.ts LIMIT 1001
                """, (rs, n) -> Map.of("date", rs.getTimestamp("ts").toInstant().atOffset(ZoneOffset.UTC).toLocalDate().toString(),
                "open", rs.getBigDecimal("open").toPlainString(), "close", rs.getBigDecimal("close").toPlainString()),
                config.ticker(), Timestamp.from(config.startDate().atStartOfDay().toInstant(ZoneOffset.UTC)),
                Timestamp.from(config.endDate().plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC))));
        if (bars.size() <= config.window() || bars.size() > 1000) throw new IllegalArgumentException("가격은 이동평균 기간보다 많고 최대 1,000개여야 합니다. 날짜 범위를 조정하세요.");
        // Validate the frozen input once, but persist only the unprocessed initial state.
        collector.replayAutoTrading(Map.of("configuration", config, "bars", bars));
        var initial = collector.replayAutoTrading(Map.of("configuration", config, "bars", List.of())).path("state");
        var keyHolder = new GeneratedKeyHolder();
        var now = Timestamp.from(Instant.now());
        jdbc.update(connection -> {
            var s = connection.prepareStatement("""
                    INSERT INTO auto_trading_runs(user_id,request_key,configuration_json,prices_json,state_json,status,
                    bar_count,interval_seconds,next_tick_at,created_at,strategy_id) VALUES(?,?,?,?,?,'RUNNING',?,?,?,?,?)
                    """, java.sql.Statement.RETURN_GENERATED_KEYS);
            s.setLong(1, owner); s.setString(2, key); s.setString(3, mapper.writeValueAsString(config));
            s.setString(4, mapper.writeValueAsString(bars)); s.setString(5, mapper.writeValueAsString(initial));
            s.setInt(6, bars.size()); s.setInt(7, config.intervalSeconds()); s.setTimestamp(8, now); s.setTimestamp(9, now);
            s.setObject(10, strategyId, java.sql.Types.BIGINT);
            return s;
        }, keyHolder);
        long id = Objects.requireNonNull(keyHolder.getKey()).longValue();
        controlEvent(id, "START");
        return owned(owner, id, false);
    }
    @Transactional
    public Run control(long owner, long id, String action) {
        var run = owned(owner, id, true);
        if ("STOP".equals(action) && "STOPPED".equals(run.status())) return run;
        if (Set.of("COMPLETED", "STOPPED").contains(run.status())) throw new IllegalArgumentException("종료된 실행은 재개할 수 없습니다.");
        if (!Set.of("PAUSE", "RESUME", "STOP").contains(action)) throw new IllegalArgumentException("잘못된 제어 명령");
        String next = switch (action) { case "PAUSE" -> "PAUSED"; case "STOP" -> "STOPPED"; default -> "RUNNING"; };
        if (!next.equals(run.status())) {
            jdbc.update("UPDATE auto_trading_runs SET status=?,last_error=NULL,next_tick_at=? WHERE id=?", next, Timestamp.from(Instant.now()), id);
            controlEvent(id, action);
        }
        return owned(owner, id, false);
    }
    public List<Map<String, Object>> due() {
        return jdbc.queryForList("SELECT id,user_id FROM auto_trading_runs WHERE status='RUNNING' AND next_tick_at<=? ORDER BY next_tick_at LIMIT 100", Timestamp.from(Instant.now()));
    }
    private JsonNode replay(Run run, int count) {
        return replay(run, count, false);
    }
    private JsonNode replay(Run run, int count, boolean comparison) {
        // shortcut: replay at most 1,000 frozen bars per tick; use incremental state before scaling beyond local simulations.
        var all = mapper.readTree(jdbc.queryForObject("SELECT prices_json FROM auto_trading_runs WHERE id=?", String.class, run.id()));
        var prefix = mapper.createArrayNode();
        for (int i=0; i<count; i++) prefix.add(all.get(i));
        return collector.replayAutoTrading(Map.of("configuration", run.configuration(), "bars", prefix, "comparison", comparison));
    }
    @Transactional(readOnly=true)
    public JsonNode comparison(long owner, long id) {
        var run = owned(owner, id, false);
        return replay(run, run.cursor(), true);
    }
    @Transactional
    public void tick(long owner, long id) {
        var run = owned(owner, id, true);
        var dueAt = jdbc.queryForObject("SELECT next_tick_at FROM auto_trading_runs WHERE id=?", Timestamp.class, id).toInstant();
        if (!"RUNNING".equals(run.status()) || dueAt.isAfter(Instant.now())) return;
        int next = run.cursor() + 1;
        JsonNode result = replay(run, next);
        JsonNode entry = result.path("entries").get(next - 1);
        if (entry == null || !result.path("state").isObject()) throw new IllegalStateException("자동 실행 결과 형식 오류");
        jdbc.update("INSERT INTO auto_trading_journal(run_id,step_number,recorded_at,payload_json) VALUES(?,?,?,?)",
                id, next, Timestamp.from(Instant.now()), mapper.writeValueAsString(entry));
        boolean completed = next == run.barCount();
        jdbc.update("UPDATE auto_trading_runs SET cursor_position=?,state_json=?,status=?,last_error=NULL,next_tick_at=? WHERE id=?",
                next, mapper.writeValueAsString(result.path("state")), completed ? "COMPLETED" : "RUNNING",
                Timestamp.from(Instant.now().plusSeconds(run.configuration().path("intervalSeconds").asInt())), id);
        if (completed) controlEvent(id, "COMPLETED");
    }
    @Transactional
    public void fail(long owner, long id) {
        var run = owned(owner, id, true);
        if (!"RUNNING".equals(run.status())) return;
        jdbc.update("UPDATE auto_trading_runs SET status='ERROR',last_error=? WHERE id=?", "자동 계산 실패. 저장 기록을 유지했습니다. 서버 상태를 확인한 후 재개하세요.", id);
        controlEvent(id, "ERROR");
    }
    @Transactional
    public Map<String, Object> verify(long owner, long id) {
        var run = owned(owner, id, true);
        var result = replay(run, run.cursor());
        var saved = mapper.createArrayNode();
        journal(id).forEach(e -> saved.add(e.payload()));
        return Map.of("matches", run.state().equals(result.path("state")) && saved.equals(result.path("entries")),
                "steps", run.cursor());
    }
}
