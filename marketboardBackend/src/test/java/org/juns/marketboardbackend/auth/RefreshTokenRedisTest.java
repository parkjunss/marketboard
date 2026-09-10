package org.juns.marketboardbackend.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.juns.marketboardbackend.security.JwtProperties;
import org.juns.marketboardbackend.security.JwtTokenProvider;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

class RefreshTokenRedisTest {
    @Test
    void concurrentRotationHasOneWinnerAndRevocationCannotBeUndone() throws Exception {
        var factory = new LettuceConnectionFactory("localhost",
                Integer.parseInt(System.getenv().getOrDefault("REDIS_PORT", "6379")));
        factory.afterPropertiesSet();
        var redis = new StringRedisTemplate(factory);
        var provider = new JwtTokenProvider(new JwtProperties(
                "test-secret-key-for-jwt-signing-must-be-at-least-32-bytes-long", 900000L, 60000L));
        var service = new RefreshTokenService(redis, provider);
        long userId = -Math.abs(UUID.randomUUID().getMostSignificantBits());
        String key = "refresh:" + userId;
        var pool = Executors.newFixedThreadPool(8);
        try {
            service.store(userId, "original");
            var start = new CountDownLatch(1);
            var results = new ArrayList<Future<Boolean>>();
            for (int i = 0; i < 8; i++) {
                String replacement = "replacement-" + i;
                results.add(pool.submit(() -> {
                    start.await();
                    return service.rotate(userId, "original", replacement);
                }));
            }
            start.countDown();
            int winners = 0;
            for (var result : results) if (result.get(10, TimeUnit.SECONDS)) winners++;
            assertThat(winners).isEqualTo(1);
            assertThat(redis.getExpire(key, TimeUnit.MILLISECONDS)).isBetween(1L, 60000L);
            String winner = redis.opsForValue().get(key);
            assertThat(service.rotate(userId, "original", "replay")).isFalse();
            assertThat(redis.opsForValue().get(key)).isEqualTo(winner);
            service.revoke(userId);
            assertThat(service.rotate(userId, winner, "resurrected")).isFalse();
            assertThat(redis.hasKey(key)).isFalse();

            // Regardless of which Redis command wins, the final state must be revoked.
            for (int i = 0; i < 20; i++) {
                service.store(userId, "original");
                var gate = new CountDownLatch(1);
                var rotate = pool.submit(() -> { gate.await(); return service.rotate(userId, "original", "next"); });
                var revoke = pool.submit(() -> { gate.await(); service.revoke(userId); return true; });
                gate.countDown();
                rotate.get(10, TimeUnit.SECONDS);
                revoke.get(10, TimeUnit.SECONDS);
                assertThat(redis.hasKey(key)).isFalse();
            }
            service.store(userId, "expired");
            redis.expire(key, java.time.Duration.ZERO);
            assertThat(service.rotate(userId, "expired", "next")).isFalse();
        } finally {
            pool.shutdownNow();
            redis.delete(key);
            factory.destroy();
        }
    }
}
