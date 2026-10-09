package org.juns.marketboardbackend.portfolio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.juns.marketboardbackend.common.exception.PortfolioLedgerConflictException;
import org.juns.marketboardbackend.portfolio.dto.PortfolioTransactionRequest;
import org.juns.marketboardbackend.security.JwtTokenProvider;
import org.juns.marketboardbackend.symbol.Symbol;
import org.juns.marketboardbackend.symbol.SymbolRepository;
import org.juns.marketboardbackend.symbol.SymbolResolutionService;
import org.juns.marketboardbackend.user.Role;
import org.juns.marketboardbackend.user.User;
import org.juns.marketboardbackend.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "marketboard.portfolio-ledger.writes-enabled=true")
class PortfolioTransactionApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JwtTokenProvider jwt;
    @Autowired UserRepository users;
    @Autowired PortfolioRepository portfolios;
    @Autowired PortfolioPositionRepository positions;
    @Autowired PortfolioTransactionRepository transactions;
    @Autowired PortfolioTransactionService transactionService;
    @Autowired SymbolRepository symbols;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean SymbolResolutionService symbolResolution;

    @Test
    void buyReplayHistoryOwnershipAndLedgerCutoverGuards() throws Exception {
        User user = user("ledger-api");
        User other = user("ledger-other");
        Portfolio portfolio = portfolios.save(Portfolio.builder().user(user).name("Main").build());
        Symbol symbol = symbols.save(Symbol.builder().ticker("API" + UUID.randomUUID().toString().substring(0, 6))
                .name("API Trade").exchange("NASDAQ").priority(1).build());
        when(symbolResolution.resolveOrFetch(symbol.getTicker())).thenReturn(symbol);
        String token = bearer(user);
        String key = UUID.randomUUID().toString();
        String request = """
                {"ticker":"%s","type":"BUY","quantity":2,"unitPrice":100,"fee":1,
                 "occurredAt":"2026-10-09T01:00:00Z"}
                """.formatted(symbol.getTicker());
        try {
            String first = mvc.perform(post("/api/portfolios/{id}/transactions", portfolio.getId())
                            .header("Authorization", token).header("Idempotency-Key", key)
                            .contentType(MediaType.APPLICATION_JSON).content(request))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.type").value("BUY"))
                    .andReturn().getResponse().getContentAsString();
            String replay = mvc.perform(post("/api/portfolios/{id}/transactions", portfolio.getId())
                            .header("Authorization", token).header("Idempotency-Key", key)
                            .contentType(MediaType.APPLICATION_JSON).content(request))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();

            assertThat(mapper.readTree(replay)).isEqualTo(mapper.readTree(first));
            assertThat(transactions.findByPortfolio_IdOrderByOccurredAtAscIdAsc(portfolio.getId())).hasSize(1);
            assertThat(positions.findByPortfolio_IdAndSymbol_Id(portfolio.getId(), symbol.getId())
                    .orElseThrow().getAvgCost()).isEqualByComparingTo("100.5000");

            mvc.perform(post("/api/portfolios/{id}/transactions", portfolio.getId())
                            .header("Authorization", token).header("Idempotency-Key", key)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request.replace("\"quantity\":2", "\"quantity\":3")))
                    .andExpect(status().isConflict());
            mvc.perform(get("/api/portfolios/{id}/transactions", portfolio.getId())
                            .header("Authorization", token))
                    .andExpect(status().isOk()).andExpect(jsonPath("$[0].type").value("BUY"));
            mvc.perform(get("/api/portfolios/{id}/transactions", portfolio.getId())
                            .header("Authorization", bearer(other)))
                    .andExpect(status().isNotFound());
            mvc.perform(post("/api/portfolios/{id}/positions", portfolio.getId())
                            .header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"ticker\":\"OTHER\",\"quantity\":1,\"avgCost\":1}"))
                    .andExpect(status().isConflict());
            mvc.perform(delete("/api/portfolios/{id}", portfolio.getId()).header("Authorization", token))
                    .andExpect(status().isConflict());
        } finally {
            jdbc.update("delete from idempotent_requests where user_id in (?, ?)", user.getId(), other.getId());
            jdbc.update("delete from portfolio_transactions where portfolio_id = ?", portfolio.getId());
            jdbc.update("delete from portfolio_positions where portfolio_id = ?", portfolio.getId());
            portfolios.deleteById(portfolio.getId());
            symbols.deleteById(symbol.getId());
            users.deleteAllById(java.util.List.of(user.getId(), other.getId()));
        }
    }

    @Test
    void concurrentSellsCannotExceedTheLockedPortfolioPosition() throws Exception {
        User user = user("ledger-concurrent");
        Portfolio portfolio = portfolios.save(Portfolio.builder().user(user).name("Main").build());
        Symbol symbol = symbols.save(Symbol.builder().ticker("CON" + UUID.randomUUID().toString().substring(0, 6))
                .name("Concurrent Trade").exchange("NASDAQ").priority(1).build());
        PortfolioPosition position = positions.save(PortfolioPosition.builder()
                .portfolio(portfolio).symbol(symbol).quantity(new BigDecimal("5"))
                .avgCost(new BigDecimal("100")).build());
        Instant openingAt = Instant.parse("2026-10-09T01:00:00Z");
        transactions.save(PortfolioTransaction.openingBalance(
                portfolio, symbol, new BigDecimal("5"), new BigDecimal("100"), position.getId(), openingAt));
        when(symbolResolution.resolveOrFetch(symbol.getTicker())).thenReturn(symbol);
        var request = new PortfolioTransactionRequest(symbol.getTicker(), PortfolioTransactionType.SELL,
                new BigDecimal("3"), new BigDecimal("110"), BigDecimal.ZERO, openingAt.plusSeconds(1));
        var pool = Executors.newFixedThreadPool(2);
        var gate = new CountDownLatch(1);
        try {
            var first = pool.submit(() -> {
                gate.await();
                return transactionService.create(user.getId(), portfolio.getId(), request, "sell-a");
            });
            var second = pool.submit(() -> {
                gate.await();
                return transactionService.create(user.getId(), portfolio.getId(), request, "sell-b");
            });
            gate.countDown();

            int successes = 0;
            int conflicts = 0;
            for (var future : java.util.List.of(first, second)) {
                try {
                    future.get(10, TimeUnit.SECONDS);
                    successes++;
                } catch (ExecutionException exception) {
                    assertThat(exception.getCause()).isInstanceOf(PortfolioLedgerConflictException.class);
                    conflicts++;
                }
            }
            assertThat(successes).isEqualTo(1);
            assertThat(conflicts).isEqualTo(1);
            assertThat(positions.findByPortfolio_IdAndSymbol_Id(portfolio.getId(), symbol.getId())
                    .orElseThrow().getQuantity()).isEqualByComparingTo("2");
        } finally {
            pool.shutdownNow();
            jdbc.update("delete from portfolio_transactions where portfolio_id = ?", portfolio.getId());
            jdbc.update("delete from portfolio_positions where portfolio_id = ?", portfolio.getId());
            portfolios.deleteById(portfolio.getId());
            symbols.deleteById(symbol.getId());
            users.deleteById(user.getId());
        }
    }

    private User user(String prefix) {
        String suffix = UUID.randomUUID().toString();
        return users.save(User.builder().email(prefix + suffix + "@test.com").username(prefix + suffix)
                .passwordHash("unused").role(Role.USER).build());
    }

    private String bearer(User user) {
        return "Bearer " + jwt.generateAccessToken(user.getId(), user.getEmail(), Role.USER);
    }
}
