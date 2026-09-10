package org.juns.marketboardbackend.review;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.juns.marketboardbackend.user.*;
import org.juns.marketboardbackend.security.JwtTokenProvider;
import org.juns.marketboardbackend.collector.CollectorClient;
import org.juns.marketboardbackend.portfolio.*;
import org.juns.marketboardbackend.symbol.*;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class ReviewApiTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired JwtTokenProvider jwt;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired PortfolioRepository portfolios;
    @Autowired PortfolioPositionRepository positions;
    @Autowired SymbolRepository symbols;
    @MockitoBean CollectorClient collector;

    @Test
    void authenticatedCreateReplayAndPartialSnapshotRoundTrip() throws Exception {
        var user = users.save(User.builder().email(UUID.randomUUID()+"@test.com").username("test").passwordHash("test").role(Role.USER).build());
        var token = "Bearer " + jwt.generateAccessToken(user.getId(), user.getEmail(), Role.USER);
        Long symbolId = null;
        try {
            mvc.perform(post("/api/portfolios").header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"test\"}"))
                    .andExpect(status().isBadRequest());
            String key = UUID.randomUUID().toString();
            String first = mvc.perform(post("/api/portfolios").header("Authorization", token).header("Idempotency-Key", key)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"test\"}"))
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
            String second = mvc.perform(post("/api/portfolios").header("Authorization", token).header("Idempotency-Key", key)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"test\"}"))
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
            assertThat(mapper.readTree(second)).isEqualTo(mapper.readTree(first));
            long portfolioId = mapper.readTree(first).get("id").asLong();
            var symbol = symbols.save(Symbol.builder().ticker("T"+UUID.randomUUID().toString().substring(0,8)).name("test").exchange("NASDAQ").priority(1).build());
            symbolId = symbol.getId();
            var position = positions.save(PortfolioPosition.builder().portfolio(portfolios.findById(portfolioId).orElseThrow())
                    .symbol(symbol).quantity(BigDecimal.TEN).avgCost(BigDecimal.ONE).build());
            String positionPath = "/api/portfolios/"+portfolioId+"/positions/"+position.getId();
            mvc.perform(patch(positionPath).header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"quantity\":15,\"avgCost\":1,\"version\":0}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
            mvc.perform(patch(positionPath).header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"quantity\":10,\"avgCost\":2,\"version\":0}"))
                    .andExpect(status().isConflict());
            mvc.perform(patch(positionPath).header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"quantity\":10,\"avgCost\":2}"))
                    .andExpect(status().isBadRequest());
            assertThat(positions.findById(position.getId()).orElseThrow().getQuantity()).isEqualByComparingTo("15");
            mvc.perform(post("/api/portfolios").header("Authorization", token).header("Idempotency-Key", key)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"changed\"}"))
                    .andExpect(status().isConflict());
            String reviewKey = UUID.randomUUID().toString();
            String review = mvc.perform(post("/api/reviews").header("Authorization", token).header("Idempotency-Key", reviewKey)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"period\":5}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.payload.schemaVersion").value(1))
                    .andExpect(jsonPath("$.payload.histories.SPX.error").isString())
                    .andReturn().getResponse().getContentAsString();
            long id = mapper.readTree(review).get("id").asLong();
            mvc.perform(get("/api/reviews/"+id).header("Authorization", token)).andExpect(status().isOk());
            String other = "Bearer " + jwt.generateAccessToken(-1L, "other@example.com", Role.USER);
            mvc.perform(get("/api/reviews/"+id).header("Authorization", other)).andExpect(status().isNotFound());
            mvc.perform(get("/api/reviews/"+id)).andExpect(status().isUnauthorized());
            mvc.perform(get("/api/reviews/"+id).header("Authorization", "Bearer invalid")).andExpect(status().isUnauthorized());
            mvc.perform(get("/api/admin/users").header("Authorization", token)).andExpect(status().isForbidden());
            String replay = mvc.perform(post("/api/reviews").header("Authorization", token).header("Idempotency-Key", reviewKey)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"period\":5}"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(mapper.readTree(replay)).isEqualTo(mapper.readTree(review));
            when(collector.runBacktest(any())).thenReturn(Optional.empty());
            String backtestKey = UUID.randomUUID().toString();
            String input = "{\"name\":\"test\",\"tickers\":[\"SPY\"],\"startDate\":\"2025-01-01\",\"endDate\":\"2025-02-01\",\"initialCapital\":1000,\"riskFreeRate\":0}";
            for (int i = 0; i < 2; i++) mvc.perform(post("/api/backtest/runs").header("Authorization", token).header("Idempotency-Key", backtestKey)
                    .contentType(MediaType.APPLICATION_JSON).content(input)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("FAILED"));
            verify(collector, times(1)).runBacktest(any());
        } finally {
            jdbc.update("delete from portfolio_positions where portfolio_id in (select id from portfolios where user_id = ?)", user.getId());
            for (String table : List.of("idempotent_requests", "investment_reviews", "backtest_runs", "portfolios"))
                jdbc.update("delete from " + table + " where user_id = ?", user.getId());
            if (symbolId != null) symbols.deleteById(symbolId);
            users.deleteById(user.getId());
        }
    }
}
