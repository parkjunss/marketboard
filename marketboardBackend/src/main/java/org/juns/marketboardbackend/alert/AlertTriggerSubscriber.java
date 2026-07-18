package org.juns.marketboardbackend.alert;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import org.juns.marketboardbackend.alert.dto.AlertNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Listens for the collector's 'alert-triggers' Redis channel (published when a tick crosses
 * an alert's target price), marks the alert triggered in MySQL, and pushes a one-shot
 * notification to the owning user's STOMP user destination.
 */
@Component
public class AlertTriggerSubscriber implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(AlertTriggerSubscriber.class);

    private final AlertRepository alertRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    public AlertTriggerSubscriber(
            AlertRepository alertRepository, SimpMessagingTemplate messagingTemplate, ObjectMapper objectMapper) {
        this.alertRepository = alertRepository;
        this.messagingTemplate = messagingTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void onMessage(Message message, byte[] pattern) {
        try {
            String body = new String(message.getBody(), StandardCharsets.UTF_8);
            JsonNode payload = objectMapper.readTree(body);
            Long alertId = payload.get("alertId").asLong();
            Long userId = payload.get("userId").asLong();

            Alert alert = alertRepository.findById(alertId).orElse(null);
            if (alert == null || !alert.isActive()) {
                return;
            }
            alert.markTriggered();

            AlertNotification notification = new AlertNotification(
                    alertId,
                    payload.get("symbol").asString(),
                    AlertCondition.valueOf(payload.get("condition").asString()),
                    new BigDecimal(payload.get("targetPrice").asString()),
                    new BigDecimal(payload.get("price").asString()));
            messagingTemplate.convertAndSendToUser(String.valueOf(userId), "/queue/alerts", notification);
        } catch (RuntimeException ex) {
            log.warn("Failed to process alert-trigger message from Redis", ex);
        }
    }
}
