package org.juns.marketboardbackend.execution;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.juns.marketboardbackend.security.*;
import org.juns.marketboardbackend.user.Role;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers=AutoTradingController.class, properties={
        "app.cors.allowed-origins=http://localhost:3100", "app.rate-limit.enabled=false"})
@Import(SecurityConfig.class)
class AutoTradingControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean AutoTradingService service;
    @MockitoBean JwtTokenProvider jwtTokenProvider;
    private final UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
            new AuthenticatedUser(7L, "test@example.test", Role.USER), null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
    @Test void allAutomationEndpointsRequireAuthentication() throws Exception {
        for (var path : List.of("", "/prices", "/1", "/1/control", "/1/verify", "/1/comparison", "/strategies", "/strategies/1/runs", "/1/compare/2",
                "/validations", "/validations/1", "/validations/1/verify", "/strategies/1/validations",
                "/validations/1/cost-analyses", "/cost-analyses/1", "/cost-analyses/1/export?format=html")) {
            mvc.perform(get("/api/auto-trading" + path)).andExpect(status().isUnauthorized());
            mvc.perform(post("/api/auto-trading" + path).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(service);
    }
    @Test void callerIdentityComesFromAuthentication() throws Exception {
        mvc.perform(get("/api/auto-trading/1").with(authentication(auth))).andExpect(status().isOk());
        verify(service).detail(7, 1);
        mvc.perform(get("/api/auto-trading/1/comparison").with(authentication(auth))).andExpect(status().isOk());
        verify(service).comparison(7, 1);
        mvc.perform(get("/api/auto-trading/strategies").with(authentication(auth))).andExpect(status().isOk());
        verify(service).strategies(7);
        mvc.perform(get("/api/auto-trading/1/compare/2").with(authentication(auth))).andExpect(status().isOk());
        verify(service).compareRuns(7, 1, 2);
        mvc.perform(post("/api/auto-trading/strategies/1/runs").with(authentication(auth)).header("Idempotency-Key", "repeat")).andExpect(status().isOk());
        verify(service).runStrategy(7, "repeat", 1);
        mvc.perform(get("/api/auto-trading/validations/1").with(authentication(auth))).andExpect(status().isOk());
        verify(service).validation(7, 1);
        mvc.perform(post("/api/auto-trading/validations/1/verify").with(authentication(auth))).andExpect(status().isOk());
        verify(service).verifyValidation(7, 1);
        mvc.perform(get("/api/auto-trading/cost-analyses/1").with(authentication(auth))).andExpect(status().isOk());
        verify(service).costAnalysis(7, 1);
    }
    @Test void invalidControlsAndConfigurationAreRejected() throws Exception {
        mvc.perform(post("/api/auto-trading/1/control").with(authentication(auth))
                .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"LIVE\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/auto-trading").with(authentication(auth)).header("Idempotency-Key", "test")
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/auto-trading/strategies").with(authentication(auth)).header("Idempotency-Key", "test")
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\" \",\"sourceRunId\":1}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/auto-trading/validations/1/cost-analyses").with(authentication(auth)).header("Idempotency-Key", "test")
                .contentType(MediaType.APPLICATION_JSON).content("{\"scenarios\":[]}")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
