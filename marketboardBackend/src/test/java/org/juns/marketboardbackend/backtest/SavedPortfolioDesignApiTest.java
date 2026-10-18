package org.juns.marketboardbackend.backtest;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.Optional;
import java.util.UUID;
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
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class SavedPortfolioDesignApiTest {
    static final String PATH="/api/portfolio/designs";
    static final String INPUT="""
        {"name":"ETF 설계","selectedId":"minimum","configuration":{
        "tickers":["SPY","QQQ"],"startDate":"2024-01-01","endDate":"2024-12-31",
        "capital":10000,"cash":1000,"maxWeightPct":80,"riskLimitPct":15,
        "validationStartDate":"2025-01-01","validationEndDate":"2025-03-31"}}
        """;
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired JwtTokenProvider jwt;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean CollectorClient collector;
    @MockitoBean org.springframework.data.redis.listener.RedisMessageListenerContainer redisListener;

    @Test void immutableOwnedSnapshotAndReplay() throws Exception {
        var user=users.save(User.builder().email(UUID.randomUUID()+"@test.com").username("test").passwordHash("test").role(Role.USER).build());
        String token="Bearer "+jwt.generateAccessToken(user.getId(),user.getEmail(),Role.USER);
        String key=UUID.randomUUID().toString();
        when(collector.designPortfolio(any())).thenReturn(Optional.of(mapper.readTree("""
            {"status":"READY","calculationVersion":"test-v1","candidates":[{"id":"minimum","periodReturnPct":12}],"frontier":[]}
            """)));
        try {
            String body=mvc.perform(post(PATH).header("Authorization",token).header("Idempotency-Key",key)
                    .contentType(MediaType.APPLICATION_JSON).content(INPUT)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.payload.configuration.validationStartDate").value("2025-01-01"))
                    .andExpect(jsonPath("$.payload.result.candidates[0].periodReturnPct").value(12))
                    .andReturn().getResponse().getContentAsString();
            long id=mapper.readTree(body).path("id").asLong();
            when(collector.designPortfolio(any())).thenReturn(Optional.of(mapper.readTree("""
                {"status":"READY","candidates":[{"id":"minimum","periodReturnPct":99}],"frontier":[]}
                """)));
            String replay=mvc.perform(post(PATH).header("Authorization",token).header("Idempotency-Key",key)
                    .contentType(MediaType.APPLICATION_JSON).content(INPUT)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(mapper.readTree(replay)).isEqualTo(mapper.readTree(body));
            mvc.perform(get(PATH+"/"+id).header("Authorization",token)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.payload.result.candidates[0].periodReturnPct").value(12));
            mvc.perform(get(PATH).header("Authorization",token)).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(id));
            String other="Bearer "+jwt.generateAccessToken(-1L,"other@test.com",Role.USER);
            mvc.perform(get(PATH+"/"+id).header("Authorization",other)).andExpect(status().isNotFound());
            mvc.perform(get(PATH).header("Authorization",other)).andExpect(status().isOk()).andExpect(content().json("[]"));
            mvc.perform(get(PATH+"/"+id)).andExpect(status().isUnauthorized());
            mvc.perform(post(PATH).header("Authorization",token).header("Idempotency-Key",key)
                    .contentType(MediaType.APPLICATION_JSON).content(INPUT.replace("ETF 설계","changed"))).andExpect(status().isConflict());
            assertThat(jdbc.queryForObject("select count(*) from saved_portfolio_designs where user_id=?",Integer.class,user.getId())).isEqualTo(1);
        } finally {
            jdbc.update("delete from idempotent_requests where user_id=?",user.getId());
            jdbc.update("delete from saved_portfolio_designs where user_id=?",user.getId());
            users.deleteById(user.getId());
        }
    }

    @Test void invalidAndUnavailableResultsNeverPersist() throws Exception {
        String token="Bearer "+jwt.generateAccessToken(-2L,"test@test.com",Role.USER);
        String key=UUID.randomUUID().toString();
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(INPUT)).andExpect(status().isUnauthorized());
        mvc.perform(post(PATH).header("Authorization",token).header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON)
                .content(INPUT.replace("2025-01-01","2024-12-31"))).andExpect(status().isBadRequest());
        verify(collector,never()).designPortfolio(any());
        when(collector.designPortfolio(any())).thenReturn(Optional.empty());
        mvc.perform(post(PATH).header("Authorization",token).header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON).content(INPUT))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.message").isNotEmpty());
        when(collector.designPortfolio(any())).thenReturn(Optional.of(mapper.readTree("{\"status\":\"INSUFFICIENT_DATA\"}")));
        mvc.perform(post(PATH).header("Authorization",token).header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON).content(INPUT))
                .andExpect(status().isBadRequest());
        when(collector.designPortfolio(any())).thenReturn(Optional.of(mapper.readTree("{\"status\":\"READY\",\"candidates\":[{\"id\":\"equal\"}]}")));
        mvc.perform(post(PATH).header("Authorization",token).header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON).content(INPUT))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("select count(*) from saved_portfolio_designs where user_id=-2",Integer.class)).isZero();
    }
}
