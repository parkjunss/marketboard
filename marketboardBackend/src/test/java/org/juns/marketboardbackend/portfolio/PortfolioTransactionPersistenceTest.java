package org.juns.marketboardbackend.portfolio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import org.juns.marketboardbackend.symbol.Symbol;
import org.juns.marketboardbackend.symbol.SymbolRepository;
import org.juns.marketboardbackend.user.Role;
import org.juns.marketboardbackend.user.User;
import org.juns.marketboardbackend.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
class PortfolioTransactionPersistenceTest {

    @Autowired PortfolioTransactionRepository transactions;
    @Autowired PortfolioRepository portfolios;
    @Autowired SymbolRepository symbols;
    @Autowired UserRepository users;

    @Test
    void persistsAndReadsLedgerInReplayOrder() {
        Fixture fixture = fixture("ledger-order@example.com", "ORD");
        Instant later = Instant.parse("2026-10-09T02:00:00Z");
        Instant earlier = Instant.parse("2026-10-09T01:00:00Z");
        transactions.save(PortfolioTransaction.buy(fixture.portfolio, fixture.symbol,
                BigDecimal.ONE, new BigDecimal("120"), BigDecimal.ZERO, later, null));
        transactions.save(PortfolioTransaction.openingBalance(fixture.portfolio, fixture.symbol,
                new BigDecimal("10"), new BigDecimal("100"), 91L, earlier));
        transactions.flush();

        assertThat(transactions.findByPortfolio_IdOrderByOccurredAtAscIdAsc(fixture.portfolio.getId()))
                .extracting(PortfolioTransaction::getTransactionType)
                .containsExactly(PortfolioTransactionType.OPENING_BALANCE, PortfolioTransactionType.BUY);
    }

    @Test
    void preventsDuplicateOpeningBalance() {
        Fixture fixture = fixture("ledger-unique@example.com", "UNQ");
        Instant now = Instant.parse("2026-10-09T01:00:00Z");
        transactions.saveAndFlush(PortfolioTransaction.openingBalance(
                fixture.portfolio, fixture.symbol, BigDecimal.ONE, BigDecimal.TEN, 92L, now));

        assertThatThrownBy(() -> transactions.saveAndFlush(PortfolioTransaction.openingBalance(
                fixture.portfolio, fixture.symbol, BigDecimal.ONE, BigDecimal.TEN, 92L, now.plusSeconds(1))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void preventsDuplicateExternalSourceTransaction() {
        Fixture fixture = fixture("ledger-source@example.com", "SRC");
        Instant now = Instant.parse("2026-10-09T01:00:00Z");
        transactions.saveAndFlush(PortfolioTransaction.buy(
                fixture.portfolio, fixture.symbol, BigDecimal.ONE, BigDecimal.TEN,
                BigDecimal.ZERO, now, "broker-1"));

        assertThatThrownBy(() -> transactions.saveAndFlush(PortfolioTransaction.buy(
                fixture.portfolio, fixture.symbol, BigDecimal.ONE, BigDecimal.TEN,
                BigDecimal.ZERO, now.plusSeconds(1), "broker-1")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void preventsReversingTheSameTransactionTwice() {
        Fixture fixture = fixture("ledger-reversal@example.com", "REV");
        Instant now = Instant.parse("2026-10-09T01:00:00Z");
        var buy = transactions.saveAndFlush(PortfolioTransaction.buy(
                fixture.portfolio, fixture.symbol, BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ZERO, now, null));
        transactions.saveAndFlush(PortfolioTransaction.reversal(
                fixture.portfolio, fixture.symbol, buy, now.plusSeconds(1)));

        assertThatThrownBy(() -> transactions.saveAndFlush(PortfolioTransaction.reversal(
                fixture.portfolio, fixture.symbol, buy, now.plusSeconds(2))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Fixture fixture(String email, String ticker) {
        User user = users.save(User.builder().email(email).username(email)
                .passwordHash("unused").role(Role.USER).build());
        Portfolio portfolio = portfolios.save(Portfolio.builder().user(user).name("Main").build());
        Symbol symbol = symbols.save(Symbol.builder().ticker(ticker).name(ticker)
                .exchange("NASDAQ").priority(1).build());
        return new Fixture(portfolio, symbol);
    }

    private record Fixture(Portfolio portfolio, Symbol symbol) {}
}
