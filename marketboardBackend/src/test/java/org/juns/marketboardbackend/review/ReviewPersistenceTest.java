package org.juns.marketboardbackend.review;

import static org.assertj.core.api.Assertions.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.juns.marketboardbackend.user.*;
import org.juns.marketboardbackend.common.IdempotencyService;
import org.juns.marketboardbackend.common.exception.ResourceNotFoundException;
import org.juns.marketboardbackend.collector.MarketIndexCandle;

@SpringBootTest
class ReviewPersistenceTest {
    @Autowired ReviewService reviews;
    @Autowired IdempotencyService idempotency;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;

    @Test
    void immutableEvidenceSurvivesSeparateReadAndIsOwnerScoped() {
        var user = users.save(User.builder().email(UUID.randomUUID()+"@test.com").username("test").passwordHash("test").role(Role.USER).build());
        try {
            var candles = new ArrayList<MarketIndexCandle>();
            candles.add(new MarketIndexCandle("2026-09-01T04:00:00Z", 100, 100, 100, 100, 0));
            var payload = new ReviewService.Payload(1, "observed-bars-v1", 5, Instant.now(), Instant.now(),
                    Map.of("SPX", new ReviewService.Resource<>(candles, null)),
                    new ReviewService.Resource<>(null, "missing"), new ReviewService.Resource<>(List.of(), null), Map.of());
            var key = UUID.randomUUID().toString();
            var saved = idempotency.execute(user.getId(), "review-create", key, 5, ReviewService.Detail.class, () -> reviews.save(user.getId(), payload));
            candles.clear();
            assertThat(reviews.get(user.getId(), saved.id())).isEqualTo(saved);
            assertThat(reviews.list(user.getId())).extracting(ReviewService.Summary::id).contains(saved.id());
            assertThatThrownBy(() -> reviews.get(-1L, saved.id())).isInstanceOf(ResourceNotFoundException.class);
            assertThat(idempotency.execute(user.getId(), "review-create", key, 5, ReviewService.Detail.class,
                    () -> { throw new AssertionError("must replay stored evidence"); })).isEqualTo(saved);
        } finally {
            jdbc.update("delete from idempotent_requests where user_id = ?", user.getId());
            jdbc.update("delete from investment_reviews where user_id = ?", user.getId());
            users.deleteById(user.getId());
        }
    }
}
