package org.juns.marketboardbackend.execution;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

@WebMvcTest(controllers = ExecutionController.class, properties = {
        "app.cors.allowed-origins=http://localhost:3100", "app.rate-limit.enabled=false"})
@Import(SecurityConfig.class)
class ExecutionControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean ExecutionService service;
    @MockitoBean ExecutionOperations operations;
    @MockitoBean JwtTokenProvider jwtTokenProvider;
    private final UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
            new AuthenticatedUser(7L, "user@example.test", Role.USER), null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
    @ParameterizedTest @ValueSource(strings = {"/api/execution", "/api/execution/1", "/api/execution/1/commands", "/api/execution/1/verify", "/api/execution/1/incidents"})
    void anonymousAccessRejected(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
    @Test void authenticatedOwnerIsUsed() throws Exception {
        mvc.perform(get("/api/execution/12").with(authentication(auth))).andExpect(status().isOk());
        verify(service).get(7, 12);
    }
    @Test void onlyFixedDemoIsPublic() throws Exception {
        mvc.perform(get("/api/execution/demo")).andExpect(status().isOk());
        verify(service).demo();
        mvc.perform(post("/api/execution/demo")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/execution/1/timeline")).andExpect(status().isUnauthorized());
    }
    @Test void collectorFailureIsReportedAfterTransactionReturns() throws Exception {
        when(service.verify(7, 12)).thenThrow(new IllegalStateException("offline"));
        mvc.perform(post("/api/execution/12/verify").with(authentication(auth))).andExpect(status().isServiceUnavailable());
        verify(operations).failure(7, 12);
    }
    @Test void arbitraryOperationsAndInvalidQuantitiesRejected() throws Exception {
        for (String body : List.of("{\"key\":\"a\",\"operation\":\"LIVE_TRADE\"}",
                "{\"key\":\"a\",\"operation\":\"BUY\",\"quantity\":\"NaN\"}")) {
            mvc.perform(post("/api/execution/12/commands").with(authentication(auth))
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }
}
