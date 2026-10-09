package org.juns.marketboardbackend.portfolio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.math.BigDecimal;
import org.juns.marketboardbackend.symbol.Symbol;
import org.juns.marketboardbackend.symbol.SymbolRepository;
import org.juns.marketboardbackend.user.Role;
import org.juns.marketboardbackend.user.User;
import org.juns.marketboardbackend.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import({PortfolioOpeningBalanceBackfillService.class, PortfolioOpeningBalanceBackfillRepository.class})
class PortfolioOpeningBalanceBackfillServiceTest {

    @Autowired PortfolioOpeningBalanceBackfillService backfill;
    @Autowired PortfolioTransactionRepository transactions;
    @Autowired PortfolioPositionRepository positions;
    @Autowired PortfolioRepository portfolios;
    @Autowired SymbolRepository symbols;
    @Autowired UserRepository users;

    @Test
    void migratesEveryPositionAndIsIdempotent() {
        Portfolio portfolio = portfolio("opening@example.com");
        position(portfolio, symbol("AAA"), "10.123456", "101.1234");
        position(portfolio, symbol("BBB"), "2", "55.5");

        var first = backfill.backfill();
        var second = backfill.backfill();

        assertThat(first.totalPositions()).isEqualTo(2);
        assertThat(first.alreadyMigrated()).isZero();
        assertThat(first.inserted()).isEqualTo(2);
        assertThat(first.totalQuantity()).isEqualByComparingTo("12.123456");
        assertThat(first.totalCost()).isEqualByComparingTo("1134.7182904704");
        assertThat(second.totalPositions()).isEqualTo(2);
        assertThat(second.alreadyMigrated()).isEqualTo(2);
        assertThat(second.inserted()).isZero();
        assertThat(transactions.findByTransactionTypeOrderBySourcePositionIdAsc(
                PortfolioTransactionType.OPENING_BALANCE))
                .extracting(PortfolioTransaction::getQuantity)
                .containsExactly(new BigDecimal("10.123456"), new BigDecimal("2.000000"));
    }

    @Test
    void mismatchedExistingOpeningBalanceFailsVerification() {
        Portfolio portfolio = portfolio("mismatch@example.com");
        Symbol symbol = symbol("MIS");
        PortfolioPosition position = position(portfolio, symbol, "10", "100");
        transactions.saveAndFlush(PortfolioTransaction.openingBalance(
                portfolio, symbol, new BigDecimal("9"), position.getAvgCost(),
                position.getId(), position.getUpdatedAt()));

        assertThatIllegalStateException().isThrownBy(backfill::backfill)
                .withMessageContaining("positionId=" + position.getId());
    }

    @Test
    void invalidPositionRollsBackWholeBatch() {
        Portfolio portfolio = portfolio("rollback@example.com");
        position(portfolio, symbol("GOOD"), "1", "10");
        position(portfolio, symbol("BAD"), "0", "10");

        assertThatIllegalStateException().isThrownBy(backfill::backfill);
        assertThat(transactions.count()).isZero();
    }

    private Portfolio portfolio(String email) {
        User user = users.save(User.builder().email(email).username(email)
                .passwordHash("unused").role(Role.USER).build());
        return portfolios.save(Portfolio.builder().user(user).name("Main").build());
    }

    private Symbol symbol(String ticker) {
        return symbols.save(Symbol.builder().ticker(ticker).name(ticker)
                .exchange("NASDAQ").priority(1).build());
    }

    private PortfolioPosition position(Portfolio portfolio, Symbol symbol, String quantity, String avgCost) {
        return positions.saveAndFlush(PortfolioPosition.builder()
                .portfolio(portfolio).symbol(symbol)
                .quantity(new BigDecimal(quantity)).avgCost(new BigDecimal(avgCost)).build());
    }
}
