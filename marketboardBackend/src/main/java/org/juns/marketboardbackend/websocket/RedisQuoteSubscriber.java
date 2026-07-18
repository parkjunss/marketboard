package org.juns.marketboardbackend.websocket;

import java.nio.charset.StandardCharsets;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class RedisQuoteSubscriber implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(RedisQuoteSubscriber.class);

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    public RedisQuoteSubscriber(SimpMessagingTemplate messagingTemplate, ObjectMapper objectMapper) {
        this.messagingTemplate = messagingTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String body = new String(message.getBody(), StandardCharsets.UTF_8);
            JsonNode payload = objectMapper.readTree(body);
            String symbol = payload.get("symbol").asString();
            messagingTemplate.convertAndSend("/topic/quotes/" + symbol, payload);
        } catch (RuntimeException ex) {
            log.warn("Failed to parse quote message from Redis 'quotes' channel", ex);
        }
    }
}
