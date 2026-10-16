package org.juns.marketboardbackend.review;

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
public class EtfPaperService {
    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");
    public record Account(long id, long reviewId, boolean active, JsonNode state, String message) {}
    public record Snapshot(long id, String kind, String date, Instant capturedAt, JsonNode payload) {}
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final ReviewService reviews;
    private final CollectorClient collector;
    public EtfPaperService(JdbcTemplate jdbc, ObjectMapper mapper, ReviewService reviews, CollectorClient collector) {
        this.jdbc = jdbc; this.mapper = mapper; this.reviews = reviews; this.collector = collector;
    }
    @Transactional
    public Account start(Long userId, Long reviewId) {
        var evidence = reviews.get(userId, reviewId).payload().etf();
        if (evidence == null || !"etf-momentum-v1".equals(evidence.calculationVersion()))
            throw new IllegalArgumentException("지원하는 ETF 전략 기록이 아닙니다.");
        var decisions = reviews.decisions(userId, reviewId);
        if (decisions.isEmpty() || decisions.get(0).choice() != ReviewDecision.Choice.EXECUTE)
            throw new IllegalArgumentException("검토 기록에 채택 판단과 이유를 먼저 저장해 주세요.");
        var state = Map.of("date", LocalDate.now(NEW_YORK).toString(), "cash", evidence.configuration().initialCapital(),
                "shares", Map.of(), "value", evidence.configuration().initialCapital(), "returnPct", 0);
        var key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("INSERT INTO etf_paper_accounts(user_id, review_id, created_at, state_json) VALUES(?,?,?,?)",
                    java.sql.Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, userId); statement.setLong(2, reviewId);
            statement.setTimestamp(3, Timestamp.from(Instant.now())); statement.setString(4, mapper.writeValueAsString(state));
            return statement;
        }, key);
        return refresh(userId, Objects.requireNonNull(key.getKey()).longValue());
    }
    public List<Account> list(Long userId) {
        return jdbc.query("SELECT * FROM etf_paper_accounts WHERE user_id=? ORDER BY id DESC", (rs, n) ->
                new Account(rs.getLong("id"), rs.getLong("review_id"), rs.getBoolean("active"),
                        mapper.readTree(rs.getString("state_json")), rs.getString("last_error")), userId);
    }
    private Account owned(Long userId, Long id, boolean lock) {
        var rows = jdbc.query("SELECT * FROM etf_paper_accounts WHERE id=? AND user_id=?" + (lock ? " FOR UPDATE" : ""),
                (rs, n) -> new Account(rs.getLong("id"), rs.getLong("review_id"), rs.getBoolean("active"),
                        mapper.readTree(rs.getString("state_json")), rs.getString("last_error")), id, userId);
        if (rows.isEmpty()) throw new ResourceNotFoundException("모의 운용 계좌를 찾을 수 없습니다.");
        return rows.get(0);
    }
    public List<Snapshot> snapshots(Long userId, Long id) {
        owned(userId, id, false);
        return journal(id);
    }
    private List<Snapshot> journal(Long id) {
        return jdbc.query("SELECT * FROM etf_paper_snapshots WHERE account_id=? ORDER BY id", (rs, n) ->
                new Snapshot(rs.getLong("id"), rs.getString("kind"), rs.getDate("snapshot_date").toString(),
                        rs.getTimestamp("created_at").toInstant(), mapper.readTree(rs.getString("payload_json"))), id);
    }
    private void append(Long id, String kind, String date, Object payload, Instant captured) {
        jdbc.update("INSERT INTO etf_paper_snapshots(account_id,kind,snapshot_date,created_at,payload_json) VALUES(?,?,?,?,?)",
                id, kind, java.sql.Date.valueOf(date), Timestamp.from(captured), mapper.writeValueAsString(payload));
    }
    @Transactional
    public Account refresh(Long userId, Long id) {
        var account = owned(userId, id, true);
        if (!account.active()) return account;
        var evidence = reviews.get(userId, account.reviewId()).payload().etf();
        var journal = journal(id);
        var currentMonthEnd = LocalDate.now(NEW_YORK).withDayOfMonth(1).minusDays(1);
        if (journal.stream().noneMatch(s -> s.kind().equals("SIGNAL") && s.date().equals(currentMonthEnd.toString()))) {
            var signal = collector.getEtfMomentum();
            if (signal.isPresent() && signal.get().ready() && currentMonthEnd.equals(signal.get().asOf())) {
                append(id, "SIGNAL", currentMonthEnd.toString(), signal.get(), Instant.now());
                journal = journal(id);
            }
        }
        var signals = journal.stream().filter(s -> s.kind().equals("SIGNAL"))
                .map(s -> Map.of("id", s.id(), "capturedAt", s.capturedAt().toString(), "signal", s.payload())).toList();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("calculationVersion", evidence.calculationVersion()); payload.put("strategyId", evidence.strategyId());
        payload.put("initialCapital", evidence.configuration().initialCapital());
        payload.put("feeBps", evidence.configuration().feeBps()); payload.put("slippageBps", evidence.configuration().slippageBps());
        payload.put("state", account.state()); payload.put("signals", signals);
        var result = collector.advanceEtfPaper(payload);
        String message = "ETF 평가 서버를 이용할 수 없습니다. 기존 기록을 유지합니다.";
        JsonNode state = account.state();
        if (result.isPresent()) {
            var response = result.get();
            for (var event : response.path("events"))
                append(id, "VALUATION", event.path("state").path("date").asText(), event, Instant.now());
            state = response.path("state");
            message = response.path("message").isNull() ? null : response.path("message").asText();
        }
        if (signals.isEmpty()) message = "유효한 월말 신호를 기다리고 있습니다. 아직 모의 체결하지 않습니다.";
        else if (journal.stream().noneMatch(s -> s.kind().equals("SIGNAL") && s.date().equals(currentMonthEnd.toString())))
            message = "이번 월말 신호가 없어 이전 보유를 유지합니다. 과거 신호를 소급 생성하지 않습니다.";
        jdbc.update("UPDATE etf_paper_accounts SET state_json=?,last_error=? WHERE id=?", mapper.writeValueAsString(state), message, id);
        return new Account(id, account.reviewId(), true, state, message);
    }
    @Transactional
    public Account stop(Long userId, Long id) {
        var account = owned(userId, id, true);
        jdbc.update("UPDATE etf_paper_accounts SET active=FALSE WHERE id=?", id);
        return new Account(id, account.reviewId(), false, account.state(), account.message());
    }
    public List<Map<String, Object>> activeOwners() {
        return jdbc.queryForList("SELECT id,user_id FROM etf_paper_accounts WHERE active=TRUE");
    }
}
