package org.juns.marketboardbackend.review;

import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class EtfPaperScheduler {
    private final EtfPaperService service;
    public EtfPaperScheduler(EtfPaperService service) { this.service = service; }
    @Scheduled(cron = "0 30 8 * * *", zone = "UTC")
    public void refresh() {
        for (var account : service.activeOwners()) {
            try { service.refresh(((Number) account.get("user_id")).longValue(), ((Number) account.get("id")).longValue()); }
            catch (RuntimeException ex) {
                LoggerFactory.getLogger(getClass()).warn("ETF paper refresh failed for account {}", account.get("id"), ex);
            }
        }
    }
}
