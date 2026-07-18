package org.juns.marketboardbackend.alert;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Redis is a cache, not the source of truth for alerts — a Redis restart (or a backend
 * restart before Redis is populated) would silently leave un-triggered alerts unmirrored.
 * Re-mirrors every untriggered alert from MySQL into Redis on startup.
 */
@Component
public class AlertMirrorInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AlertMirrorInitializer.class);

    private final AlertRepository alertRepository;
    private final AlertRedisMirror alertRedisMirror;

    public AlertMirrorInitializer(AlertRepository alertRepository, AlertRedisMirror alertRedisMirror) {
        this.alertRepository = alertRepository;
        this.alertRedisMirror = alertRedisMirror;
    }

    @Override
    public void run(ApplicationArguments args) {
        var activeAlerts = alertRepository.findByTriggeredAtIsNull();
        activeAlerts.forEach(alertRedisMirror::mirror);
        log.info("Re-mirrored {} active alert(s) into Redis on startup", activeAlerts.size());
    }
}
