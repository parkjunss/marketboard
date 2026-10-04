package org.juns.marketboardbackend.auth;

import java.time.Duration;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class OAuthLoginCodeService {
    private static final String KEY_PREFIX = "oauth-login:";
    private final StringRedisTemplate redisTemplate;

    public OAuthLoginCodeService(StringRedisTemplate redisTemplate) { this.redisTemplate = redisTemplate; }

    public String create(Long userId) {
        String code = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(KEY_PREFIX + code, userId.toString(), Duration.ofMinutes(2));
        return code;
    }

    public Long consume(String code) {
        String value = redisTemplate.opsForValue().getAndDelete(KEY_PREFIX + code);
        return value == null ? null : Long.valueOf(value);
    }
}
