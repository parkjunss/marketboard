package org.juns.marketboardbackend.portfolio;

import static org.assertj.core.api.Assertions.*;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.dao.OptimisticLockingFailureException;
import org.juns.marketboardbackend.user.*;
import org.juns.marketboardbackend.symbol.*;
import org.juns.marketboardbackend.portfolio.dto.PortfolioPositionUpdateRequest;

@SpringBootTest
class PortfolioConcurrencyTest {
    @Autowired PortfolioRepository portfolios;
    @Autowired PortfolioPositionRepository positions;
    @Autowired UserRepository users;
    @Autowired SymbolRepository symbols;
    @Autowired PortfolioService service;
    @Autowired PlatformTransactionManager manager;

    @Test
    void staleClientAndConcurrentDatabaseWritesCannotOverwrite() throws Exception {
        var tx = new TransactionTemplate(manager);
        var user = users.save(User.builder().email(UUID.randomUUID()+"@test.com").username("test").passwordHash("test").role(Role.USER).build());
        var symbol = symbols.save(Symbol.builder().ticker("T"+UUID.randomUUID().toString().substring(0,8)).name("test").exchange("NASDAQ").priority(1).build());
        var portfolio = portfolios.save(Portfolio.builder().user(user).name("test").build());
        var position = positions.save(PortfolioPosition.builder().portfolio(portfolio).symbol(symbol).quantity(BigDecimal.TEN).avgCost(BigDecimal.ONE).build());
        var pool = Executors.newFixedThreadPool(2);
        try {
            var barrier = new CyclicBarrier(2);
            Callable<Boolean> update = () -> {
                try {
                    tx.executeWithoutResult(status -> {
                        var row = positions.findById(position.getId()).orElseThrow();
                        try { barrier.await(10, TimeUnit.SECONDS); } catch (Exception e) { throw new RuntimeException(e); }
                        row.update(new BigDecimal("15"), BigDecimal.ONE);
                        positions.flush();
                    });
                    return true;
                } catch (OptimisticLockingFailureException expected) { return false; }
            };
            var first = pool.submit(update); var second = pool.submit(update);
            assertThat(first.get(15, TimeUnit.SECONDS) ^ second.get(15, TimeUnit.SECONDS)).isTrue();
            assertThat(positions.findById(position.getId()).orElseThrow().getVersion()).isEqualTo(1);
            assertThatThrownBy(() -> service.updatePosition(user.getId(), portfolio.getId(), position.getId(),
                    new PortfolioPositionUpdateRequest(BigDecimal.ONE, BigDecimal.TEN, 0L)))
                    .isInstanceOf(OptimisticLockingFailureException.class);
            assertThat(positions.findById(position.getId()).orElseThrow().getQuantity()).isEqualByComparingTo("15");
        } finally {
            pool.shutdownNow(); positions.deleteById(position.getId()); portfolios.deleteById(portfolio.getId());
            symbols.deleteById(symbol.getId()); users.deleteById(user.getId());
        }
    }
}
