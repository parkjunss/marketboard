package org.juns.marketboardbackend.execution;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AutoTradingScheduler {
    private final AutoTradingService service;
    public AutoTradingScheduler(AutoTradingService service) { this.service = service; }
    @Scheduled(fixedDelayString="${execution.auto.poll-ms:1000}")
    public void advance() {
        for (var row : service.due()) {
            long owner = ((Number) row.get("user_id")).longValue(), id = ((Number) row.get("id")).longValue();
            try { service.tick(owner, id); }
            catch (RuntimeException ex) {
                org.slf4j.LoggerFactory.getLogger(getClass()).warn("Auto simulation {} failed; pausing with journal preserved", id, ex);
                try { service.fail(owner, id); }
                catch (RuntimeException failure) { org.slf4j.LoggerFactory.getLogger(getClass()).error("Cannot pause simulation {}", id, failure); }
            }
        }
    }
}
