package org.juns.marketboardbackend.execution;

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
public class ExecutionService {
    public record Run(long id, long version, JsonNode configuration, JsonNode state) {}
    public record Difference(String path, JsonNode stored, JsonNode replayed) {}
    public record CommandEntry(long sequence, java.time.Instant recordedAt, JsonNode command) {}
    public record Verification(boolean matches, String storedHash, String replayedHash, long commandCount, List<Difference> differences) {}
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final CollectorClient collector;
    private final ExecutionOperations operations;

    public ExecutionService(JdbcTemplate jdbc, ObjectMapper mapper, CollectorClient collector, ExecutionOperations operations) {
        this.jdbc = jdbc; this.mapper = mapper; this.collector = collector;
        this.operations = operations;
    }

    private Run read(long owner, long id, boolean lock) {
        var rows = jdbc.query("SELECT * FROM execution_runs WHERE id=? AND user_id=?" + (lock ? " FOR UPDATE" : ""),
                (rs, n) -> new Run(rs.getLong("id"), rs.getLong("version"),
                        mapper.readTree(rs.getString("configuration_json")), mapper.readTree(rs.getString("state_json"))), id, owner);
        if (rows.isEmpty()) throw new ResourceNotFoundException("모의 실행을 찾을 수 없습니다.");
        return rows.get(0);
    }

    public Run get(long owner, long id) { return read(owner, id, false); }
    public JsonNode demo() {
        var config = new ExecutionController.Configuration("execution-v1", "10000", "5", "5000", "0.8");
        var history = List.of(new ExecutionController.Command("buy", "BUY", null, "10"),
                new ExecutionController.Command("dispatch", "DISPATCH", "buy", null),
                new ExecutionController.Command("timeout", "TIMEOUT", "buy", null),
                new ExecutionController.Command("partial", "PARTIAL_FILL", "buy", "4"),
                new ExecutionController.Command("cancel", "CANCEL", "buy", null),
                new ExecutionController.Command("recover", "REVERSE", "buy", null));
        return collector.replayExecution(Map.of("configuration", config, "commands", history));
    }
    public List<CommandEntry> timeline(long owner, long id) {
        read(owner, id, false);
        return jdbc.query("SELECT * FROM execution_commands WHERE run_id=? ORDER BY sequence_no", (rs, n) ->
                new CommandEntry(rs.getLong("sequence_no"), rs.getTimestamp("created_at").toInstant(), mapper.readTree(rs.getString("command_json"))), id);
    }

    public List<Run> list(long owner) {
        return jdbc.query("SELECT * FROM execution_runs WHERE user_id=? ORDER BY id DESC LIMIT 30",
                (rs, n) -> new Run(rs.getLong("id"), rs.getLong("version"), mapper.readTree(rs.getString("configuration_json")),
                        mapper.readTree(rs.getString("state_json"))), owner);
    }

    @Transactional
    public Run create(long owner, String key, ExecutionController.Configuration configuration) {
        // Serialize creation per owner as well as protecting retries with a unique key.
        jdbc.queryForObject("SELECT id FROM users WHERE id=? FOR UPDATE", Long.class, owner);
        var config = mapper.valueToTree(configuration);
        var existing = jdbc.queryForList("SELECT id FROM execution_runs WHERE user_id=? AND request_key=?", Long.class, owner, key);
        if (!existing.isEmpty()) {
            var run = read(owner, existing.get(0), false);
            if (!run.configuration().equals(config)) throw new IllegalArgumentException("같은 요청 키에 다른 설정을 사용할 수 없습니다.");
            return run;
        }
        var state = collector.replayExecution(Map.of("configuration", config, "commands", List.of()));
        var holder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("INSERT INTO execution_runs(user_id,request_key,configuration_json,state_json,cash,reserved_cash,shares,reserved_shares) VALUES(?,?,?,?,?,?,?,?)",
                    new String[]{"id"});
            statement.setLong(1, owner); statement.setString(2, key);
            statement.setString(3, mapper.writeValueAsString(config)); statement.setString(4, mapper.writeValueAsString(state));
            statement.setBigDecimal(5, amount(state, "cash")); statement.setBigDecimal(6, amount(state, "reservedCash"));
            statement.setBigDecimal(7, amount(state, "shares")); statement.setBigDecimal(8, amount(state, "reservedShares"));
            return statement;
        }, holder);
        return read(owner, Objects.requireNonNull(holder.getKey()).longValue(), false);
    }

    private List<JsonNode> commands(long id) {
        return jdbc.query("SELECT command_json FROM execution_commands WHERE run_id=? ORDER BY sequence_no",
                (rs, n) -> mapper.readTree(rs.getString("command_json")), id);
    }

    private java.math.BigDecimal amount(JsonNode state, String field) {
        return new java.math.BigDecimal(state.path(field).asText());
    }

    @Transactional
    public Run apply(long owner, long id, ExecutionController.Command command) {
        var run = read(owner, id, true);
        var event = mapper.valueToTree(command);
        var history = commands(id);
        var previous = history.stream().filter(c -> c.path("key").asText().equals(command.key())).findFirst();
        if (previous.isPresent()) {
            if (!previous.get().equals(event)) throw new IllegalArgumentException("같은 명령 키에 다른 요청을 사용할 수 없습니다.");
            return run;
        }
        if (jdbc.queryForObject("SELECT COUNT(*) FROM execution_incidents WHERE run_id=? AND kind='REPLAY_MISMATCH' AND active=TRUE", Integer.class, id) > 0)
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,
                    "리플레이 불일치가 해결될 때까지 실행 변경이 차단됩니다.");
        if (history.size() >= 500) throw new IllegalArgumentException("실행당 최대 500개 명령입니다.");
        history.add(event);
        // Collector is pure: failure before commit has no external order side effect.
        var state = collector.replayExecution(Map.of("configuration", run.configuration(), "commands", history));
        jdbc.update("INSERT INTO execution_commands(run_id,sequence_no,request_key,command_json) VALUES(?,?,?,?)",
                id, run.version() + 1, command.key(), mapper.writeValueAsString(event));
        jdbc.update("UPDATE execution_runs SET state_json=?,cash=?,reserved_cash=?,shares=?,reserved_shares=?,version=version+1 WHERE id=?",
                mapper.writeValueAsString(state), amount(state, "cash"), amount(state, "reservedCash"),
                amount(state, "shares"), amount(state, "reservedShares"), id);
        operations.observe(id, state);
        return new Run(id, run.version() + 1, run.configuration(), state);
    }

    @Transactional
    public Verification verify(long owner, long id) {
        var run = read(owner, id, true);
        var history = commands(id);
        var replayed = collector.replayExecution(Map.of("configuration", run.configuration(), "commands", history));
        var differences = new ArrayList<Difference>();
        differences("", run.state(), replayed, differences);
        operations.condition(id, "REPLAY_MISMATCH", !run.state().equals(replayed), "저장 상태와 재생 결과가 다릅니다. 신규 주문을 중지하고 차이를 검토하세요.");
        operations.condition(id, "PROCESSING_FAILURE", false, "계산 요청 실패");
        return new Verification(run.state().equals(replayed), run.state().path("economicHash").asText(),
                replayed.path("economicHash").asText(), history.size(), differences);
    }
    private void differences(String path, JsonNode stored, JsonNode replayed, List<Difference> result) {
        if (Objects.equals(stored, replayed) || result.size() >= 50) return;
        if (stored != null && replayed != null && stored.isObject() && replayed.isObject()) {
            var keys = new TreeSet<String>();
            stored.properties().forEach(e -> keys.add(e.getKey())); replayed.properties().forEach(e -> keys.add(e.getKey()));
            for (String key : keys) differences(path + "/" + key.replace("~", "~0").replace("/", "~1"), stored.get(key), replayed.get(key), result);
        } else result.add(new Difference(path, stored, replayed));
    }
}
