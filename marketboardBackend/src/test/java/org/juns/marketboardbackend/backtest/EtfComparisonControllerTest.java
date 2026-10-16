package org.juns.marketboardbackend.backtest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import org.juns.marketboardbackend.collector.CollectorClient;
import org.juns.marketboardbackend.collector.EtfComparisonResult;
import org.juns.marketboardbackend.security.JwtTokenProvider;
import org.juns.marketboardbackend.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@WebMvcTest(controllers = EtfComparisonController.class, properties = {
        "app.cors.allowed-origins=http://localhost:3100", "app.rate-limit.enabled=false"})
@Import(SecurityConfig.class)
class EtfComparisonControllerTest {
    private static final String PATH = "/api/backtest/etf-comparison";
    private static final String REQUEST = """
            {"startDate":"2024-10-01","endDate":"2024-11-29", "initialCapital":10000,"feeBps":5,"slippageBps":5}
            """;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockitoBean CollectorClient collector;
    @MockitoBean JwtTokenProvider jwtTokenProvider;

    @Test
    void anonymousRequestDoesNotRunCalculation() throws Exception {
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(REQUEST)).andExpect(status().isUnauthorized());
        verifyNoInteractions(collector);
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "101", "null"})
    void invalidCostsAreRejectedBeforeCollector(String fee) throws Exception {
        mvc.perform(post(PATH).with(user("investor")).contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST.replace("\"feeBps\":5", "\"feeBps\":" + fee)))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(collector);
    }

    @ParameterizedTest
    @ValueSource(strings = {"2025-01-01", "2000-01-01", "1990-01-01"})
    void invalidPeriodIsRejectedBeforeCollector(String start) throws Exception {
        mvc.perform(post(PATH).with(user("investor")).contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST.replace("2024-10-01", start)))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(collector);
    }

    @Test
    void incompleteCurrentSessionCannotBeBacktested() throws Exception {
        String today = LocalDate.now(ZoneId.of("America/New_York")).toString();
        mvc.perform(post(PATH).with(user("investor")).contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST.replace("2024-11-29", today)))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(collector);
    }

    @Test
    void noDataDoesNotTurnIntoAZeroReturnResult() throws Exception {
        var result = mapper.readValue("{\"ready\":false,\"message\":\"GLD 일봉 부족\",\"strategies\":[]}", EtfComparisonResult.class);
        when(collector.compareEtfStrategies(any())).thenReturn(Optional.of(result));
        mvc.perform(post(PATH).with(user("investor")).contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isOk()).andExpect(jsonPath("$.ready").value(false))
                .andExpect(jsonPath("$.strategies").isEmpty()).andExpect(jsonPath("$.message").value("GLD 일봉 부족"));
    }

    @Test
    void offlineCollectorReturns503() throws Exception {
        when(collector.compareEtfStrategies(any())).thenReturn(Optional.empty());
        mvc.perform(post(PATH).with(user("investor")).contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.message").isNotEmpty());
    }
}
