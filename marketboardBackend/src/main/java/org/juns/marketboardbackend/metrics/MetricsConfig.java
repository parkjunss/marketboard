package org.juns.marketboardbackend.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.stereotype.Component;

/**
 * Registers a gauge backed by {@link SimpUserRegistry}, which Spring's STOMP infrastructure
 * already keeps current on every connect/disconnect — no new event listeners needed.
 */
@Component
public class MetricsConfig {

    public MetricsConfig(MeterRegistry registry, SimpUserRegistry simpUserRegistry) {
        registry.gauge(
                "marketboard.stomp.sessions.active",
                simpUserRegistry,
                r -> r.getUsers().stream().mapToInt(user -> user.getSessions().size()).sum());
    }
}
