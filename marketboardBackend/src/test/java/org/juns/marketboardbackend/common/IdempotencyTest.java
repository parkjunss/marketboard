package org.juns.marketboardbackend.common;

import static org.assertj.core.api.Assertions.*;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.juns.marketboardbackend.user.*;
import org.juns.marketboardbackend.portfolio.*;
import org.juns.marketboardbackend.portfolio.dto.*;

@SpringBootTest
class IdempotencyTest {
    @Autowired IdempotencyService idempotency;
    @Autowired PortfolioService portfolios;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;

    @Test
    void concurrentReplayPayloadMismatchOwnershipAndRollback() throws Exception {
        var user = users.save(User.builder().email(UUID.randomUUID()+"@test.com").username("test").passwordHash("test").role(Role.USER).build());
        var other = users.save(User.builder().email(UUID.randomUUID()+"@test.com").username("test").passwordHash("test").role(Role.USER).build());
        var key = UUID.randomUUID().toString();
        var calls = new AtomicInteger();
        var pool = Executors.newFixedThreadPool(4);
        try {
            var gate = new CountDownLatch(1);
            Callable<PortfolioSummaryResponse> create = () -> {
                gate.await();
                return idempotency.execute(user.getId(), "portfolio-create", key, "same", PortfolioSummaryResponse.class,
                        () -> { calls.incrementAndGet(); return portfolios.createPortfolio(user.getId(), "same"); });
            };
            var first = pool.submit(create); var second = pool.submit(create); gate.countDown();
            var saved = first.get(15, TimeUnit.SECONDS);
            assertThat(second.get(15, TimeUnit.SECONDS)).isEqualTo(saved);
            assertThat(calls.get()).isEqualTo(1);
            assertThat(idempotency.execute(user.getId(), "portfolio-create", key, "same", PortfolioSummaryResponse.class,
                    () -> { throw new AssertionError("replay must not execute"); })).isEqualTo(saved);
            assertThatThrownBy(() -> idempotency.execute(user.getId(), "portfolio-create", key, "different", String.class, () -> "wrong"))
                    .isInstanceOf(IdempotencyService.IdempotencyConflictException.class);
            var independent = idempotency.execute(other.getId(), "portfolio-create", key, "same", PortfolioSummaryResponse.class,
                    () -> portfolios.createPortfolio(other.getId(), "same"));
            assertThat(independent.id()).isNotEqualTo(saved.id());
            var failedKey = UUID.randomUUID().toString();
            assertThatThrownBy(() -> idempotency.execute(user.getId(), "portfolio-create", failedKey, "rollback", String.class, () -> {
                portfolios.createPortfolio(user.getId(), "must-rollback"); throw new IllegalStateException("fail before commit");
            })).isInstanceOf(IllegalStateException.class);
            assertThat(jdbc.queryForObject("select count(*) from portfolios where user_id = ? and name = 'must-rollback'", Integer.class, user.getId())).isZero();
            assertThat(idempotency.execute(user.getId(), "portfolio-create", failedKey, "rollback", String.class, () -> "retried")).isEqualTo("retried");
        } finally {
            pool.shutdownNow();
            for (var id : new Long[]{user.getId(), other.getId()}) {
                jdbc.update("delete from idempotent_requests where user_id = ?", id);
                jdbc.update("delete from portfolios where user_id = ?", id);
                users.deleteById(id);
            }
        }
    }
}
