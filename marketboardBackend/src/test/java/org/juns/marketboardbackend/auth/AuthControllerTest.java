package org.juns.marketboardbackend.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.juns.marketboardbackend.auth.dto.LoginRequest;
import org.juns.marketboardbackend.auth.dto.RefreshRequest;
import org.juns.marketboardbackend.auth.dto.SignupRequest;
import org.juns.marketboardbackend.auth.dto.TokenResponse;
import org.juns.marketboardbackend.common.exception.AccountSuspendedException;
import org.juns.marketboardbackend.common.exception.InvalidCredentialsException;
import org.juns.marketboardbackend.security.AuthenticatedUser;
import org.juns.marketboardbackend.security.JwtProperties;
import org.juns.marketboardbackend.security.JwtTokenProvider;
import org.juns.marketboardbackend.security.SecurityConfig;
import org.juns.marketboardbackend.user.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import tools.jackson.databind.ObjectMapper;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerTest {

    private static final String JWT_SECRET = "test-secret-key-for-jwt-signing-must-be-at-least-32-bytes-long";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;

    @TestConfiguration
    static class JwtTestConfig implements WebMvcConfigurer {
        @Bean
        JwtProperties jwtProperties() {
            return new JwtProperties(JWT_SECRET, 900_000L, 604_800_000L);
        }

        @Bean
        JwtTokenProvider jwtTokenProvider(JwtProperties jwtProperties) {
            return new JwtTokenProvider(jwtProperties);
        }

        @Override
        public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
            resolvers.add(new AuthenticationPrincipalArgumentResolver());
        }
    }

    @Test
    void signup_validRequest_returnsCreated() throws Exception {
        SignupRequest request = new SignupRequest("new@example.com", "password123", "newbie");

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        verify(authService).signup(eq(request));
    }

    @Test
    void signup_invalidEmail_returnsBadRequestAndSkipsService() throws Exception {
        SignupRequest request = new SignupRequest("not-an-email", "password123", "newbie");

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(authService, never()).signup(any());
    }

    @Test
    void login_validCredentials_returnsTokens() throws Exception {
        LoginRequest request = new LoginRequest("user@example.com", "password123");
        when(authService.login(eq(request))).thenReturn(new TokenResponse("access-token", "refresh-token"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-token"));
    }

    @Test
    void login_invalidCredentials_returnsUnauthorized() throws Exception {
        LoginRequest request = new LoginRequest("user@example.com", "wrong-password");
        when(authService.login(eq(request))).thenThrow(new InvalidCredentialsException());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_suspendedAccount_returnsForbidden() throws Exception {
        LoginRequest request = new LoginRequest("suspended@example.com", "password123");
        when(authService.login(eq(request))).thenThrow(new AccountSuspendedException());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void refresh_validToken_returnsNewTokens() throws Exception {
        RefreshRequest request = new RefreshRequest("some-refresh-token");
        when(authService.refresh(request.refreshToken()))
                .thenReturn(new TokenResponse("new-access-token", "new-refresh-token"));

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new-access-token"));
    }

    @Test
    void refresh_blankToken_returnsBadRequest() throws Exception {
        RefreshRequest request = new RefreshRequest("");

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(authService, never()).refresh(any());
    }

    @Test
    void logout_authenticatedUser_revokesTokenAndReturnsNoContent() throws Exception {
        AuthenticatedUser principal = new AuthenticatedUser(42L, "user@example.com", Role.USER);
        var authToken = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));

        mockMvc.perform(post("/api/auth/logout").with(authentication(authToken)))
                .andExpect(status().isNoContent());

        verify(authService).logout(42L);
    }
}
