package org.juns.marketboardbackend.screener;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.juns.marketboardbackend.collector.CollectorClient;
import org.juns.marketboardbackend.collector.EtfMomentumResult;
import org.juns.marketboardbackend.security.JwtTokenProvider;
import org.juns.marketboardbackend.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = EtfMomentumController.class, properties = {
        "app.cors.allowed-origins=http://localhost:3100", "app.rate-limit.enabled=false"})
@Import(SecurityConfig.class)
class EtfMomentumControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean CollectorClient collector;
    @MockitoBean JwtTokenProvider jwtTokenProvider;

    @Test
    void anonymousRequestDoesNotRunCalculation() throws Exception {
        mvc.perform(get("/api/screener/etf-momentum")).andExpect(status().isUnauthorized());
        verifyNoInteractions(collector);
    }

    @Test
    void returnsServerDatesAndWeightsWithoutRecalculation() throws Exception {
        var row = new EtfMomentumResult.Row("SPY", 1, new BigDecimal("12.5"), new BigDecimal("200"),
                new BigDecimal("190"), LocalDate.parse("2025-09-30"), new BigDecimal("0.3333333333333333"), "상위 3위 · 200일선 상회");
        var result = new EtfMomentumResult(LocalDate.parse("2025-09-30"), LocalDate.parse("2024-09-30"),
                LocalDate.parse("2025-08-31"), true, List.of(row), new BigDecimal("0.6666666666666666"));
        when(collector.getEtfMomentum()).thenReturn(Optional.of(result));
        mvc.perform(get("/api/screener/etf-momentum").with(user("investor")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.asOf").value("2025-09-30"))
                .andExpect(jsonPath("$.rows[0].momentum").value(12.5))
                .andExpect(jsonPath("$.rows[0].weight").value(0.3333333333333333))
                .andExpect(jsonPath("$.cashWeight").value(0.6666666666666666));
    }

    @Test
    void incompleteDataRemainsAWithheldResult() throws Exception {
        var result = new EtfMomentumResult(LocalDate.parse("2025-09-30"), LocalDate.parse("2024-09-30"),
                LocalDate.parse("2025-08-31"), false, List.of(
                        new EtfMomentumResult.Row("SPY", null, null, null, null, null, null, "일봉 데이터 없음")), null);
        when(collector.getEtfMomentum()).thenReturn(Optional.of(result));
        mvc.perform(get("/api/screener/etf-momentum").with(user("investor")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ready").value(false))
                .andExpect(jsonPath("$.cashWeight").isEmpty())
                .andExpect(jsonPath("$.rows[0].rank").isEmpty())
                .andExpect(jsonPath("$.rows[0].reason").value("일봉 데이터 없음"));
    }

    @Test
    void offlineCollectorIs503InsteadOfFakeCashSignal() throws Exception {
        when(collector.getEtfMomentum()).thenReturn(Optional.empty());
        mvc.perform(get("/api/screener/etf-momentum").with(user("investor")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }
}
