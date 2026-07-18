package org.juns.marketboardbackend.alert;

import java.util.Map;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Mirrors active alerts into Redis so the Python collector can check them on every tick
 * without querying MySQL. `alert:{id}` holds the alert's details; `alerts:{ticker}` is the
 * set of active alert ids for that symbol.
 */
@Component
public class AlertRedisMirror {

    private static final String ALERT_KEY_PREFIX = "alert:";
    private static final String SYMBOL_ALERTS_KEY_PREFIX = "alerts:";

    private final StringRedisTemplate redisTemplate;

    public AlertRedisMirror(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void mirror(Alert alert) {
        String alertId = String.valueOf(alert.getId());
        Map<String, String> fields = Map.of(
                "userId", String.valueOf(alert.getUser().getId()),
                "ticker", alert.getSymbol().getTicker(),
                "condition", alert.getCondition().name(),
                "targetPrice", alert.getTargetPrice().toPlainString());
        redisTemplate.opsForHash().putAll(ALERT_KEY_PREFIX + alertId, fields);
        redisTemplate.opsForSet().add(SYMBOL_ALERTS_KEY_PREFIX + alert.getSymbol().getTicker(), alertId);
    }

    public void remove(Alert alert) {
        String alertId = String.valueOf(alert.getId());
        redisTemplate.delete(ALERT_KEY_PREFIX + alertId);
        redisTemplate.opsForSet().remove(SYMBOL_ALERTS_KEY_PREFIX + alert.getSymbol().getTicker(), alertId);
    }
}
