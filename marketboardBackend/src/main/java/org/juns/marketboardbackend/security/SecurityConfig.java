package org.juns.marketboardbackend.security;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.http.HttpMethod;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.juns.marketboardbackend.auth.GoogleOAuthSuccessHandler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RateLimitFilter rateLimitFilter;
    private final ObjectProvider<GoogleOAuthSuccessHandler> googleOAuthSuccessHandler;
    private final ObjectProvider<ClientRegistrationRepository> clientRegistrations;

    @Value("${app.cors.allowed-origins}")
    private List<String> allowedOrigins;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter, RateLimitFilter rateLimitFilter,
                          ObjectProvider<GoogleOAuthSuccessHandler> googleOAuthSuccessHandler,
                          ObjectProvider<ClientRegistrationRepository> clientRegistrations) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.rateLimitFilter = rateLimitFilter;
        this.googleOAuthSuccessHandler = googleOAuthSuccessHandler;
        this.clientRegistrations = clientRegistrations;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .exceptionHandling(errors -> errors.authenticationEntryPoint((request, response, exception) -> {
                    response.setStatus(401);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"message\":\"Authentication required\"}");
                }))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/signup", "/api/auth/login", "/api/auth/refresh",
                                "/api/auth/password/forgot", "/api/auth/password/reset",
                                "/api/auth/oauth/exchange", "/oauth2/**", "/login/oauth2/**").permitAll()
                        .requestMatchers("/ws/**").permitAll()
                        // Public market-overview data (indices, breadth, sentiment, sector rotation) --
                        // none of it is user-specific, so it's shown on the unauthenticated /overview
                        // page. No mutating endpoints live under these paths for non-admins (the admin
                        // recompute trigger is under /api/admin/**, gated separately below).
                        .requestMatchers("/api/market-indices/**", "/api/market-breadth", "/api/market-sentiment/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/quotes/**", "/api/news/**").permitAll()
                        // /actuator/prometheus is scraped by Prometheus itself (no JWT to send), so it's
                        // permitAll like health/info — order matters here, this must come before the
                        // ADMIN-gated /actuator/** rule below or it'd be shadowed by it.
                        .requestMatchers("/actuator/health", "/actuator/info", "/actuator/prometheus").permitAll()
                        .requestMatchers("/actuator/**").hasRole("ADMIN")
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(rateLimitFilter, JwtAuthenticationFilter.class);
        if (clientRegistrations.getIfAvailable() != null) {
            GoogleOAuthSuccessHandler handler = googleOAuthSuccessHandler.getObject();
            http.oauth2Login(oauth -> oauth.successHandler(handler).failureHandler(handler));
        }
        return http.build();
    }

    private CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
