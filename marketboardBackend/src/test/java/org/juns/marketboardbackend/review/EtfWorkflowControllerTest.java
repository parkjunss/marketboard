package org.juns.marketboardbackend.review;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.juns.marketboardbackend.collector.*;
import java.util.function.Supplier;
import java.time.Instant;
import org.juns.marketboardbackend.common.IdempotencyService;
import org.juns.marketboardbackend.portfolio.*;
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

@WebMvcTest(controllers = {EtfWorkflowController.class, EtfPaperController.class}, properties = {
        "app.cors.allowed-origins=http://localhost:3100", "app.rate-limit.enabled=false"})
@Import(SecurityConfig.class)
class EtfWorkflowControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean CollectorClient collector;
    @MockitoBean ReviewService reviews;
    @MockitoBean EtfPaperService paper;
    @MockitoBean IdempotencyService idempotency;
    @MockitoBean PortfolioService portfolios;
    @MockitoBean PortfolioStrategyService rules;
    @MockitoBean JwtTokenProvider jwtTokenProvider;
    private final UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
            new AuthenticatedUser(7L, "user@example.test", Role.USER), null, List.of(new SimpleGrantedAuthority("ROLE_USER")));

    @ParameterizedTest @ValueSource(strings = {"/api/etf/reviews", "/api/etf/paper", "/api/etf/paper/1/snapshots"})
    void anonymousReadRejected(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        verifyNoInteractions(reviews, paper, collector);
    }
    @ParameterizedTest @ValueSource(strings = {"/api/etf/reviews", "/api/etf/rebalance", "/api/etf/paper", "/api/etf/paper/1/refresh", "/api/etf/paper/1/stop"})
    void anonymousWriteRejected(String path) throws Exception {
        mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
        verifyNoInteractions(reviews, paper, collector);
    }
    @Test void listUsesAuthenticatedOwner() throws Exception {
        when(paper.list(7L)).thenReturn(List.of());
        mvc.perform(get("/api/etf/paper").with(authentication(auth))).andExpect(status().isOk());
        verify(paper).list(7L);
    }
    @Test void negativeCashRejectedBeforeReadingPortfolio() throws Exception {
        mvc.perform(post("/api/etf/rebalance").with(authentication(auth)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reviewId\":1,\"portfolioId\":2,\"cashUsd\":-1}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(reviews, portfolios, collector);
    }
    @Test void missingStrategyRejectedBeforeCapture() throws Exception {
        mvc.perform(post("/api/etf/reviews").with(authentication(auth)).header("Idempotency-Key", java.util.UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON).content("{\"configuration\":{}}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(reviews, collector, idempotency);
    }
    @Test void refreshUsesOwnerAndAccount() throws Exception {
        mvc.perform(post("/api/etf/paper/99/refresh").with(authentication(auth))).andExpect(status().isOk());
        verify(paper).refresh(7L, 99L);
    }
    @Test void captureSavesOnlyServerEvidenceAndUsesIdempotency() throws Exception {
        var comparison = mock(EtfComparisonResult.class);
        when(comparison.ready()).thenReturn(true);
        var evidence = new EtfResearchEvidence("etf-momentum-v1", "MOMENTUM_TREND", null, comparison, null);
        when(collector.captureEtfResearch(any(), eq("MOMENTUM_TREND"))).thenReturn(Optional.of(evidence));
        when(idempotency.execute(eq(7L), eq("etf-review"), anyString(), any(), eq(ReviewService.Detail.class), any()))
                .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(5)).get());
        when(reviews.save(eq(7L), any())).thenAnswer(invocation -> {
            ReviewService.Payload payload = invocation.getArgument(1);
            assertThat(payload.etf()).isSameAs(evidence);
            assertThat(payload.schemaVersion()).isEqualTo(4);
            return new ReviewService.Detail(100L, 21, Instant.now(), payload);
        });
        mvc.perform(post("/api/etf/reviews").with(authentication(auth)).header("Idempotency-Key", java.util.UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON).content("""
                  {"strategyId":"MOMENTUM_TREND","configuration":{"startDate":"2024-01-01","endDate":"2024-12-31",
                   "initialCapital":10000,"feeBps":5,"slippageBps":5}}
                  """)).andExpect(status().isOk());
        verify(reviews).save(eq(7L), any());
    }
}
