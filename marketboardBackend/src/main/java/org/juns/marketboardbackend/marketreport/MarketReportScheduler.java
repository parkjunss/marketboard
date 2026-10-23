package org.juns.marketboardbackend.marketreport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.beans.factory.annotation.Qualifier;
import java.time.Duration;
import java.time.Instant;

@Component
public class MarketReportScheduler {
    private static final Logger log = LoggerFactory.getLogger(MarketReportScheduler.class);
    private final MarketReportService service;
    private final TaskScheduler scheduler;
    public MarketReportScheduler(MarketReportService service, @Qualifier("taskScheduler") TaskScheduler scheduler) {
        this.service = service; this.scheduler = scheduler;
    }
    @Scheduled(cron = "${market-report.cron:0 40 8 * * *}", zone = "UTC")
    public void publish() {
        attempt(0);
    }

    private void attempt(int retry) {
        try {
            var result = service.generate();
            log.info("Market report {}: tradingDate={}, reportId={}, retry={}, reasons={}", result.state(), result.tradingDate(), result.reportId(), retry, result.reasons());
            if ("DELAYED".equals(result.state()) && retry < 2) {
                scheduler.schedule(() -> attempt(retry + 1), Instant.now().plus(Duration.ofMinutes(30)));
            }
        } catch (RuntimeException ex) {
            log.warn("Market report generation failed; no automatic short-interval retry", ex);
        }
    }
}
