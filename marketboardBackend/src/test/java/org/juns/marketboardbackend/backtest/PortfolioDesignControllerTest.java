package org.juns.marketboardbackend.backtest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.Optional;
import org.juns.marketboardbackend.collector.CollectorClient;
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

@WebMvcTest(controllers=PortfolioDesignController.class, properties={"app.cors.allowed-origins=http://localhost:3100", "app.rate-limit.enabled=false"})
@Import(SecurityConfig.class)
class PortfolioDesignControllerTest {
    private static final String PATH="/api/portfolio/design";
    private static final String REQUEST="""
        {"tickers":["SPY","QQQ"],"startDate":"2024-01-01","endDate":"2024-12-31",
         "capital":10000,"cash":1000,"maxWeightPct":80,"riskFreePct":3,"riskLimitPct":15}
        """;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockitoBean CollectorClient collector;
    @MockitoBean JwtTokenProvider jwtTokenProvider;

    @Test void anonymousCannotCalculate() throws Exception {
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(REQUEST)).andExpect(status().isUnauthorized());
        verifyNoInteractions(collector);
    }
    @ParameterizedTest @ValueSource(strings={"10001","-1","null"})
    void invalidCashCannotReachCollector(String cash) throws Exception {
        mvc.perform(post(PATH).with(user("investor")).contentType(MediaType.APPLICATION_JSON)
                .content(REQUEST.replace("\"cash\":1000", "\"cash\":"+cash))).andExpect(status().isBadRequest());
        verifyNoInteractions(collector);
    }
    @Test void partialResultsRemainVisible() throws Exception {
        when(collector.designPortfolio(any())).thenReturn(Optional.of(mapper.readTree("""
            {"status":"INSUFFICIENT_DATA","assets":[{"ticker":"SPY","volatilityPct":20},{"ticker":"QQQ","issue":"일봉 없음"}],"candidates":[]}
            """)));
        mvc.perform(post(PATH).with(user("investor")).contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isOk()).andExpect(jsonPath("$.assets[0].volatilityPct").value(20))
                .andExpect(jsonPath("$.status").value("INSUFFICIENT_DATA"));
    }
    @Test void offlineIsAnErrorNotEmptyData() throws Exception {
        when(collector.designPortfolio(any())).thenReturn(Optional.empty());
        mvc.perform(post(PATH).with(user("investor")).contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.message").isNotEmpty());
    }
    @Test void duplicateTickersCannotMasqueradeAsTwoAssets() throws Exception {
        mvc.perform(post(PATH).with(user("investor")).contentType(MediaType.APPLICATION_JSON).content(REQUEST.replace("QQQ","spy")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(collector);
    }
}
