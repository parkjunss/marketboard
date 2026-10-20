package org.juns.marketboardbackend.execution;

import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import io.micrometer.core.instrument.MeterRegistry;
import org.juns.marketboardbackend.common.exception.ResourceNotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

@Service
public class ExecutionOperations {
    public record Incident(String kind, boolean active, Instant openedAt, Instant resolvedAt, long occurrences, long ageSeconds, String message) {}
    private final JdbcTemplate jdbc;
    private final Map<String, java.util.concurrent.atomic.AtomicInteger> gauges = new LinkedHashMap<>();
    public ExecutionOperations(JdbcTemplate jdbc, MeterRegistry registry) {
        this.jdbc = jdbc;
        for (String kind : List.of("UNKNOWN", "UNRECONCILED", "STALE_PRICE", "RISK_BLOCKED", "PROCESSING_FAILURE", "REPLAY_MISMATCH")) {
            var value = new java.util.concurrent.atomic.AtomicInteger();
            gauges.put(kind, value);
            registry.gauge("marketboard.execution.incidents", List.of(io.micrometer.core.instrument.Tag.of("kind", kind)), value);
        }
    }
    private void owner(long user, long id) {
        if (jdbc.queryForList("SELECT id FROM execution_runs WHERE id=? AND user_id=? FOR UPDATE", Long.class, id, user).isEmpty())
            throw new ResourceNotFoundException("모의 실행을 찾을 수 없습니다.");
    }
    // Callers hold the run row lock; the same condition never creates repeated alerts.
    void condition(long id, String kind, boolean active, String message) {
        var rows = jdbc.queryForList("SELECT active FROM execution_incidents WHERE run_id=? AND kind=?", Boolean.class, id, kind);
        if (rows.isEmpty()) {
            if (active) jdbc.update("INSERT INTO execution_incidents(run_id,kind,active,opened_at,message) VALUES(?,?,TRUE,?,?)",
                    id, kind, Timestamp.from(Instant.now()), message);
        } else if (rows.get(0) != active) {
            if (active) jdbc.update("UPDATE execution_incidents SET active=TRUE,opened_at=?,resolved_at=NULL,occurrences=occurrences+1,message=? WHERE run_id=? AND kind=?",
                    Timestamp.from(Instant.now()), message, id, kind);
            else jdbc.update("UPDATE execution_incidents SET active=FALSE,resolved_at=? WHERE run_id=? AND kind=?", Timestamp.from(Instant.now()), id, kind);
        }
    }
    void observe(long id, JsonNode state) {
        boolean unknown = false, unreconciled = false;
        for (var entry : state.path("orders").properties()) {
            unknown |= "UNKNOWN".equals(entry.getValue().path("status").asText());
            unreconciled |= state.path("broker").path(entry.getKey()).path("events").size() > entry.getValue().path("sequence").asInt();
        }
        condition(id, "UNKNOWN", unknown, "주문 결과 불명: 재주문하지 말고 거래소 접수 여부를 대사하세요.");
        condition(id, "UNRECONCILED", unreconciled, "거래소 이벤트가 아직 계좌에 반영되지 않았습니다. 체결 대사를 실행하세요.");
        var log = state.path("log");
        var last = log.isEmpty() ? null : log.get(log.size() - 1);
        boolean blocked = last != null && "BLOCKED".equals(last.path("kind").asText());
        boolean stale = blocked && last.path("reason").asText().contains("신선도");
        condition(id, "STALE_PRICE", stale, "모의 가격 지연으로 주문 차단. 새로운 정상 명령으로 복구를 확인하세요.");
        condition(id, "RISK_BLOCKED", blocked && !stale, last == null ? "" : last.path("reason").asText());
        condition(id, "PROCESSING_FAILURE", false, "계산 요청 실패");
    }
    @Transactional
    public void failure(long user, long id) {
        owner(user, id);
        condition(id, "PROCESSING_FAILURE", true, "계산 서버 요청 실패. 저장 상태를 유지했습니다. 같은 요청 키로 재시도하세요.");
    }
    @Transactional
    public List<Incident> list(long user, long id) {
        owner(user, id);
        return jdbc.query("SELECT * FROM execution_incidents WHERE run_id=? ORDER BY active DESC,opened_at DESC", (rs, n) -> {
            var opened = rs.getTimestamp("opened_at").toInstant();
            var resolved = rs.getTimestamp("resolved_at");
            return new Incident(rs.getString("kind"), rs.getBoolean("active"), opened, resolved == null ? null : resolved.toInstant(),
                    rs.getLong("occurrences"), Math.max(0, Duration.between(opened, resolved == null ? Instant.now() : resolved.toInstant()).toSeconds()), rs.getString("message"));
        }, id);
    }
    @Scheduled(fixedDelayString="${execution.metrics.fixed-delay-ms:15000}")
    public void sample() {
        for (var entry : gauges.entrySet()) entry.getValue().set(jdbc.queryForObject(
                "SELECT COUNT(*) FROM execution_incidents WHERE active=TRUE AND kind=?", Integer.class, entry.getKey()));
    }
}
