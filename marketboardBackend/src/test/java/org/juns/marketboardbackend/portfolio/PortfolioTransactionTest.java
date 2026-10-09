package org.juns.marketboardbackend.portfolio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import java.time.Instant;
import org.juns.marketboardbackend.symbol.Symbol;
import org.junit.jupiter.api.Test;

class PortfolioTransactionTest {

    private final Portfolio portfolio = mock(Portfolio.class);
    private final Symbol symbol = mock(Symbol.class);
    private final Instant occurredAt = Instant.parse("2026-10-09T01:00:00Z");

    @Test
    void createsSupportedLedgerEntries() {
        var opening = PortfolioTransaction.openingBalance(
                portfolio, symbol, new BigDecimal("10.000000"), new BigDecimal("100.0000"), 7L, occurredAt);
        var buy = PortfolioTransaction.buy(
                portfolio, symbol, new BigDecimal("2.000000"), new BigDecimal("110.0000"),
                new BigDecimal("0.5000"), occurredAt.plusSeconds(1), " broker-1 ");
        var sell = PortfolioTransaction.sell(
                portfolio, symbol, BigDecimal.ONE, new BigDecimal("120"), BigDecimal.ZERO,
                occurredAt.plusSeconds(2), null);
        var reversal = PortfolioTransaction.reversal(portfolio, symbol, sell, occurredAt.plusSeconds(3));

        assertThat(opening.getTransactionType()).isEqualTo(PortfolioTransactionType.OPENING_BALANCE);
        assertThat(opening.getSourcePositionId()).isEqualTo(7L);
        assertThat(buy.getSourceTransactionKey()).isEqualTo("broker-1");
        assertThat(sell.getFee()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(reversal.getReversalOf()).isSameAs(sell);
        assertThat(reversal.getQuantity()).isNull();
        assertThat(reversal.getUnitPrice()).isNull();
    }

    @Test
    void rejectsValuesThatCannotFormAValidLedgerEntry() {
        assertThatIllegalArgumentException().isThrownBy(() -> PortfolioTransaction.buy(
                portfolio, symbol, BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ZERO, occurredAt, null));
        assertThatIllegalArgumentException().isThrownBy(() -> PortfolioTransaction.sell(
                portfolio, symbol, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, occurredAt, null));
        assertThatIllegalArgumentException().isThrownBy(() -> PortfolioTransaction.buy(
                portfolio, symbol, BigDecimal.ONE, BigDecimal.ONE, new BigDecimal("-0.01"), occurredAt, null));
        assertThatIllegalArgumentException().isThrownBy(() -> PortfolioTransaction.openingBalance(
                portfolio, symbol, BigDecimal.ONE, BigDecimal.ONE, null, occurredAt));
        assertThatIllegalArgumentException().isThrownBy(() -> PortfolioTransaction.buy(
                portfolio, symbol, new BigDecimal("1.0000001"), BigDecimal.ONE,
                BigDecimal.ZERO, occurredAt, null));
        assertThatIllegalArgumentException().isThrownBy(() -> PortfolioTransaction.buy(
                portfolio, symbol, BigDecimal.ONE, new BigDecimal("1.00001"),
                BigDecimal.ZERO, occurredAt, null));
    }

    @Test
    void reversalMustMatchOriginalAndCannotReverseAReversal() {
        var original = PortfolioTransaction.buy(
                portfolio, symbol, BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ZERO, occurredAt, null);
        var reversal = PortfolioTransaction.reversal(portfolio, symbol, original, occurredAt.plusSeconds(1));

        assertThatIllegalArgumentException().isThrownBy(() -> PortfolioTransaction.reversal(
                mock(Portfolio.class), symbol, original, occurredAt.plusSeconds(1)));
        assertThatIllegalArgumentException().isThrownBy(() -> PortfolioTransaction.reversal(
                portfolio, mock(Symbol.class), original, occurredAt.plusSeconds(1)));
        assertThatIllegalArgumentException().isThrownBy(() -> PortfolioTransaction.reversal(
                portfolio, symbol, reversal, occurredAt.plusSeconds(2)));
    }
}
