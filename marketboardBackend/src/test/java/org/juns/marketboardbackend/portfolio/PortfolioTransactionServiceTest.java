package org.juns.marketboardbackend.portfolio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import org.juns.marketboardbackend.common.exception.PortfolioLedgerConflictException;
import org.juns.marketboardbackend.portfolio.dto.PortfolioTransactionRequest;
import org.juns.marketboardbackend.symbol.Symbol;
import org.juns.marketboardbackend.symbol.SymbolRepository;
import org.juns.marketboardbackend.symbol.SymbolResolutionService;
import org.juns.marketboardbackend.user.Role;
import org.juns.marketboardbackend.user.User;
import org.juns.marketboardbackend.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@DataJpaTest
@Import(PortfolioTransactionService.class)
@TestPropertySource(properties = "marketboard.portfolio-ledger.writes-enabled=true")
class PortfolioTransactionServiceTest {

    @Autowired PortfolioTransactionService service;
    @Autowired PortfolioTransactionRepository transactions;
    @Autowired PortfolioPositionRepository positions;
    @Autowired PortfolioRepository portfolios;
    @Autowired SymbolRepository symbolRepository;
    @Autowired UserRepository users;
    @MockitoBean SymbolResolutionService symbolResolution;

    @Test
    void buyIncludesFeeInMovingAverageThenPartialAndFullSellKeepAverageCost() {
        Fixture fixture = fixture("trade@example.com", "TRD", "10", "100");
        Instant openingAt = Instant.parse("2026-10-09T01:00:00Z");
        transactions.saveAndFlush(PortfolioTransaction.openingBalance(
                fixture.portfolio(), fixture.symbol(), new BigDecimal("10"), new BigDecimal("100"),
                fixture.position().getId(), openingAt));

        service.create(fixture.user().getId(), fixture.portfolio().getId(),
                request(PortfolioTransactionType.BUY, "5", "130", "5", openingAt.plusSeconds(1)), "buy-1");
        PortfolioPosition afterBuy = position(fixture);
        assertThat(afterBuy.getQuantity()).isEqualByComparingTo("15");
        assertThat(afterBuy.getAvgCost()).isEqualByComparingTo("110.3333");

        service.create(fixture.user().getId(), fixture.portfolio().getId(),
                request(PortfolioTransactionType.SELL, "3", "120", "2", openingAt.plusSeconds(2)), "sell-1");
        PortfolioPosition afterPartialSell = position(fixture);
        assertThat(afterPartialSell.getQuantity()).isEqualByComparingTo("12");
        assertThat(afterPartialSell.getAvgCost()).isEqualByComparingTo("110.3333");

        service.create(fixture.user().getId(), fixture.portfolio().getId(),
                request(PortfolioTransactionType.SELL, "12", "125", "0", openingAt.plusSeconds(3)), "sell-2");
        assertThat(positions.findByPortfolio_IdAndSymbol_Id(
                fixture.portfolio().getId(), fixture.symbol().getId())).isEmpty();
        assertThat(transactions.findByPortfolio_IdOrderByOccurredAtAscIdAsc(fixture.portfolio().getId()))
                .extracting(PortfolioTransaction::getTransactionType)
                .containsExactly(PortfolioTransactionType.OPENING_BALANCE, PortfolioTransactionType.BUY,
                        PortfolioTransactionType.SELL, PortfolioTransactionType.SELL);
    }

    @Test
    void overSellAndOutOfOrderTradeLeavePositionAndLedgerUnchanged() {
        Fixture fixture = fixture("reject@example.com", "REJ", "2", "50");
        Instant openingAt = Instant.parse("2026-10-09T01:00:00Z");
        transactions.saveAndFlush(PortfolioTransaction.openingBalance(
                fixture.portfolio(), fixture.symbol(), new BigDecimal("2"), new BigDecimal("50"),
                fixture.position().getId(), openingAt));

        assertThatThrownBy(() -> service.create(fixture.user().getId(), fixture.portfolio().getId(),
                request(PortfolioTransactionType.SELL, "3", "60", "0", openingAt.plusSeconds(1)), "too-much"))
                .isInstanceOf(PortfolioLedgerConflictException.class);
        assertThatThrownBy(() -> service.create(fixture.user().getId(), fixture.portfolio().getId(),
                request(PortfolioTransactionType.BUY, "1", "60", "0", openingAt.minusSeconds(1)), "old"))
                .isInstanceOf(PortfolioLedgerConflictException.class);

        assertThat(position(fixture).getQuantity()).isEqualByComparingTo("2");
        assertThat(transactions.findByPortfolio_IdOrderByOccurredAtAscIdAsc(fixture.portfolio().getId())).hasSize(1);
    }

    private PortfolioTransactionRequest request(
            PortfolioTransactionType type, String quantity, String price, String fee, Instant occurredAt) {
        return new PortfolioTransactionRequest("ignored", type, new BigDecimal(quantity),
                new BigDecimal(price), new BigDecimal(fee), occurredAt);
    }

    private PortfolioPosition position(Fixture fixture) {
        return positions.findByPortfolio_IdAndSymbol_Id(fixture.portfolio().getId(), fixture.symbol().getId())
                .orElseThrow();
    }

    private Fixture fixture(String email, String ticker, String quantity, String avgCost) {
        User user = users.save(User.builder().email(email).username(email)
                .passwordHash("unused").role(Role.USER).build());
        Portfolio portfolio = portfolios.save(Portfolio.builder().user(user).name("Main").build());
        Symbol symbol = symbolRepository.save(Symbol.builder().ticker(ticker).name(ticker)
                .exchange("NASDAQ").priority(1).build());
        PortfolioPosition position = positions.saveAndFlush(PortfolioPosition.builder()
                .portfolio(portfolio).symbol(symbol).quantity(new BigDecimal(quantity))
                .avgCost(new BigDecimal(avgCost)).build());
        when(symbolResolution.resolveOrFetch("ignored")).thenReturn(symbol);
        return new Fixture(user, portfolio, symbol, position);
    }

    private record Fixture(User user, Portfolio portfolio, Symbol symbol, PortfolioPosition position) {}
}
