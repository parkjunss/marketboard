package org.juns.marketboardbackend.security;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.juns.marketboardbackend.analysis.AnalysisController;
import org.juns.marketboardbackend.analysis.AnalysisService;
import org.juns.marketboardbackend.financials.FinancialsController;
import org.juns.marketboardbackend.financials.FinancialsService;
import org.juns.marketboardbackend.indicator.IndicatorController;
import org.juns.marketboardbackend.indicator.IndicatorRepository;
import org.juns.marketboardbackend.screener.ScreenerController;
import org.juns.marketboardbackend.screener.ScreenerService;
import org.juns.marketboardbackend.symbol.SymbolProfileController;
import org.juns.marketboardbackend.symbol.SymbolProfileService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {SymbolProfileController.class, FinancialsController.class,
        AnalysisController.class, IndicatorController.class, ScreenerController.class},
        properties = {"app.cors.allowed-origins=http://localhost:3100", "app.rate-limit.enabled=false"})
@Import(SecurityConfig.class)
class PublicAnalysisSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean JwtTokenProvider jwtTokenProvider;
    @MockitoBean SymbolProfileService profiles;
    @MockitoBean FinancialsService financials;
    @MockitoBean AnalysisService analysis;
    @MockitoBean IndicatorRepository indicators;
    @MockitoBean ScreenerService screener;

    @Test void anonymousReadsReachMarketServices() throws Exception {
        mvc.perform(get("/api/symbols/AAPL/profile")).andExpect(status().isOk());
        mvc.perform(get("/api/financials/AAPL")).andExpect(status().isOk());
        mvc.perform(get("/api/analysis/AAPL")).andExpect(status().isOk());
        mvc.perform(get("/api/indicators/AAPL")).andExpect(status().isOk());
        verify(profiles).getProfile("AAPL");
        verify(financials).getFinancials("AAPL");
        verify(analysis).analyze("AAPL", null, null, null);
        verify(indicators).findBySymbol_TickerIgnoreCaseAndTimeframe("AAPL", "1d");
    }

    @Test void anonymousSearchIsReadOnlyAndValidated() throws Exception {
        mvc.perform(post("/api/screener/search").contentType(MediaType.APPLICATION_JSON).content("{\"page\":0,\"size\":20}"))
                .andExpect(status().isOk());
        verify(screener).search(any());
        clearInvocations(screener);
        mvc.perform(post("/api/screener/search").contentType(MediaType.APPLICATION_JSON).content("{\"page\":0,\"size\":101}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(screener);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/watchlist", "/api/alerts", "/api/chart-indicator-settings", "/api/portfolio/designs",
            "/api/portfolio/designs/1", "/api/portfolios", "/api/etf/reviews", "/api/etf/paper",
            "/api/reviews", "/api/reports/1", "/api/admin/symbols", "/api/screener/momentum"})
    void privateReadsStillRequireLogin(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/watchlist", "/api/alerts", "/api/portfolio/designs", "/api/etf/reviews",
            "/api/etf/paper", "/api/etf/rebalance", "/api/execution", "/api/financials/AAPL"})
    void writesStillRequireLogin(String path) throws Exception {
        mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }
}
