package org.juns.marketboardbackend.execution;

import java.nio.file.*;
import java.util.*;
import javax.sql.DataSource;
import jakarta.servlet.Filter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.core.env.MapPropertySource;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.servlet.context.AnnotationConfigServletWebServerApplicationContext;
import org.springframework.boot.web.servlet.*;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.bind.annotation.*;
import org.juns.marketboardbackend.collector.CollectorClient;
import org.juns.marketboardbackend.common.GlobalExceptionHandler;
import org.juns.marketboardbackend.security.*;
import org.juns.marketboardbackend.user.Role;
import tools.jackson.databind.ObjectMapper;

/** Loopback-only UI fixture. Real execution API, SQL and JWT validation; fixture login, no Redis. */
@TestConfiguration
@EnableWebMvc
@EnableWebSecurity
@EnableTransactionManagement
@Import({ExecutionController.class, ExecutionService.class, ExecutionOperations.class, CollectorClient.class,
        SecurityConfig.class, JwtAuthenticationFilter.class, RateLimitFilter.class, JwtTokenProvider.class,
        GlobalExceptionHandler.class, ExecutionPreview.Login.class})
public class ExecutionPreview {
    @Bean static org.springframework.context.support.PropertySourcesPlaceholderConfigurer placeholders() {
        return new org.springframework.context.support.PropertySourcesPlaceholderConfigurer();
    }
    public static void main(String[] args) throws Exception {
        var context = new AnnotationConfigServletWebServerApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("preview", Map.of(
                "app.cors.allowed-origins", "http://localhost:13110", "app.rate-limit.enabled", "true",
                "collector.base-url", "http://127.0.0.1:18011")));
        context.register(ExecutionPreview.class);
        context.refresh();
        var jdbc = context.getBean(JdbcTemplate.class);
        jdbc.execute("CREATE TABLE IF NOT EXISTS users(id BIGINT PRIMARY KEY)");
        jdbc.execute("MERGE INTO users KEY(id) VALUES(1),(2)");
        if (jdbc.queryForObject("SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME='EXECUTION_RUNS'", Integer.class) == 0) {
            for (String migration : List.of("V40__create_execution_runs.sql", "V41__create_execution_incidents.sql"))
                for (String sql : Files.readString(Path.of("src/main/resources/db/migration", migration)).split(";"))
                    if (!sql.isBlank()) jdbc.execute(sql);
        }
        System.out.println("EXECUTION_PREVIEW_READY http://localhost:18091 (fixture login only)");
    }
    @Bean TomcatServletWebServerFactory server() throws Exception {
        var factory = new TomcatServletWebServerFactory(18091);
        factory.setAddress(java.net.InetAddress.getByName("127.0.0.1"));
        return factory;
    }
    @Bean ServletRegistrationBean<DispatcherServlet> dispatcher(org.springframework.web.context.WebApplicationContext context) {
        return new ServletRegistrationBean<>(new DispatcherServlet(context), "/");
    }
    @Bean FilterRegistrationBean<Filter> security(@Qualifier("springSecurityFilterChain") Filter filter) {
        return new FilterRegistrationBean<>(filter);
    }
    @Bean DataSource dataSource() { return new DriverManagerDataSource("jdbc:h2:file:./build/execution-preview;MODE=MySQL;LOCK_TIMEOUT=15000", "sa", ""); }
    @Bean JdbcTemplate jdbc(DataSource source) { return new JdbcTemplate(source); }
    @Bean PlatformTransactionManager transactions(DataSource source) { return new DataSourceTransactionManager(source); }
    @Bean ObjectMapper mapper() { return new ObjectMapper(); }
    @Bean io.micrometer.core.instrument.MeterRegistry registry() { return new io.micrometer.core.instrument.simple.SimpleMeterRegistry(); }
    @Bean JwtProperties jwt() { return new JwtProperties("execution-preview-only-local-fixture-secret", 3600000, 3600000); }

    @RestController
    static class Login {
        private final JwtTokenProvider tokens;
        Login(JwtTokenProvider tokens) { this.tokens = tokens; }
        @PostMapping("/api/auth/login")
        Map<String, String> login(@RequestBody Map<String, String> body) {
            String email = body.get("email");
            if (!Set.of("owner@example.test", "other@example.test").contains(email) || !"Preview-only-123!".equals(body.get("password")))
                throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED);
            long id = email.startsWith("owner") ? 1 : 2;
            return Map.of("accessToken", tokens.generateAccessToken(id, email, Role.USER), "refreshToken", tokens.generateRefreshToken(id, email, Role.USER));
        }
        @PostMapping("/api/auth/refresh")
        Map<String, String> refresh(@RequestBody Map<String, String> body) {
            var claims = tokens.parseClaims(body.get("refreshToken"));
            if (!"REFRESH".equals(claims.get("type", String.class))) throw new IllegalArgumentException("Refresh token required");
            return Map.of("accessToken", tokens.generateAccessToken(Long.valueOf(claims.getSubject()), claims.get("email", String.class), Role.USER), "refreshToken", body.get("refreshToken"));
        }
        @GetMapping("/api/notifications") Map<String, Object> notifications() { return Map.of("items", List.of(), "unreadCount", 0); }
    }
}
