package org.juns.marketboardbackend.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.atomic.AtomicInteger;
import org.juns.marketboardbackend.collector.CollectorClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically polls the collector's {@code /health} so its WS reconnect count and connection
 * state are visible as Prometheus gauges — {@link CollectorClient#getHealth()} itself is
 * otherwise only called on-demand (from the admin dashboard), which isn't enough to graph over
 * time.
 */
@Component
public class CollectorMetricsPoller {

    private final CollectorClient collectorClient;
    private final AtomicInteger reconnectCount = new AtomicInteger(0);
    private final AtomicInteger wsConnected = new AtomicInteger(0);

    public CollectorMetricsPoller(CollectorClient collectorClient, MeterRegistry registry) {
        this.collectorClient = collectorClient;
        registry.gauge("marketboard.collector.reconnect.count", reconnectCount);
        registry.gauge("marketboard.collector.ws.connected", wsConnected);
    }

    @Scheduled(fixedRate = 30_000)
    public void poll() {
        collectorClient.getHealth().ifPresent(health -> {
            reconnectCount.set(health.reconnectCount());
            wsConnected.set(health.wsConnected() ? 1 : 0);
        });
    }
}
