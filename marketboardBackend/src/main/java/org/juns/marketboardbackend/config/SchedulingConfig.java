package org.juns.marketboardbackend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Without an explicit TaskScheduler bean, Spring's @EnableScheduling infrastructure falls back to
 * a single-threaded scheduler -- every @Scheduled method in the app (IndicatorCalculationService's
 * 5-minute recompute, MarketBreadthService's daily recompute, CollectorMetricsPoller's 30-second
 * health poll) would then run on the SAME thread, one at a time. A long-running recompute would
 * delay/skip the 30-second health poll until it finishes. Three threads is enough for one per job
 * to never block another; this app has no more scheduled jobs than that today.
 */
@Configuration
public class SchedulingConfig {

    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(3);
        scheduler.setThreadNamePrefix("scheduled-task-");
        scheduler.initialize();
        return scheduler;
    }
}
