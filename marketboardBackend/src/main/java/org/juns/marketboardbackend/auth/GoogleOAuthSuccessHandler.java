package org.juns.marketboardbackend.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class GoogleOAuthSuccessHandler implements AuthenticationSuccessHandler, AuthenticationFailureHandler {
    private final GoogleOAuthService service;
    private final String frontendUrl;

    public GoogleOAuthSuccessHandler(GoogleOAuthService service,
                                     @Value("${app.frontend-url:http://localhost:3100}") String frontendUrl) {
        this.service = service;
        this.frontendUrl = frontendUrl;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        try {
            String code = service.completeLogin((OAuth2User) authentication.getPrincipal());
            if (request.getSession(false) != null) request.getSession(false).invalidate();
            response.sendRedirect(UriComponentsBuilder.fromUriString(frontendUrl)
                    .path("/oauth/callback").queryParam("code", code).build().encode().toUriString());
        } catch (RuntimeException exception) {
            response.sendRedirect(UriComponentsBuilder.fromUriString(frontendUrl)
                    .path("/login").queryParam("oauthError", "google").build().encode().toUriString());
        }
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        response.sendRedirect(UriComponentsBuilder.fromUriString(frontendUrl)
                .path("/login").queryParam("oauthError", "google").build().encode().toUriString());
    }
}
