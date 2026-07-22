package org.juns.marketboardbackend.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import tools.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import org.juns.marketboardbackend.common.ErrorResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> AUTH_PATHS =
            Set.of("/api/auth/login", "/api/auth/signup", "/api/auth/refresh");
    private static final String ADMIN_PREFIX = "/api/admin/";

    // Plain ConcurrentHashMaps here would grow forever -- every distinct client IP that ever hits
    // a rate-limited path mints a permanent entry that's never removed. /api/auth/login in
    // particular is a common bot/scanner target, and /overview being public now means arbitrary
    // internet traffic reaches this filter. A bucket that hasn't been touched in 10 minutes is
    // safe to drop: its owner would get a fresh, full bucket on their next request anyway, same as
    // if we'd kept the (already fully refilled) old one around.
    private final ObjectMapper objectMapper;
    private final Cache<String, Bucket> authBuckets =
            Caffeine.newBuilder().maximumSize(10_000).expireAfterAccess(Duration.ofMinutes(10)).build();
    private final Cache<String, Bucket> adminBuckets =
            Caffeine.newBuilder().maximumSize(10_000).expireAfterAccess(Duration.ofMinutes(10)).build();

    @Value("${app.rate-limit.enabled:true}")
    private boolean enabled;

    // Brute-force-sensitive and unauthenticated, so the default is tight.
    @Value("${app.rate-limit.auth-capacity:5}")
    private int authCapacity;

    // Authenticated but still public-facing: a looser cap just to blunt abuse/bugs.
    @Value("${app.rate-limit.admin-capacity:60}")
    private int adminCapacity;

    public RateLimitFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!enabled) {
            filterChain.doFilter(request, response);
            return;
        }
        Bucket bucket = resolveBucket(request.getRequestURI(), clientIp(request));
        if (bucket == null || bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
            return;
        }
        respondTooManyRequests(response);
    }

    private Bucket resolveBucket(String path, String clientIp) {
        if (AUTH_PATHS.contains(path)) {
            return authBuckets.get(clientIp, ip -> newBucket(authCapacity, Duration.ofMinutes(1)));
        }
        if (path.startsWith(ADMIN_PREFIX)) {
            return adminBuckets.get(clientIp, ip -> newBucket(adminCapacity, Duration.ofMinutes(1)));
        }
        return null;
    }

    private Bucket newBucket(int capacity, Duration period) {
        Bandwidth limit = Bandwidth.builder().capacity(capacity).refillGreedy(capacity, period).build();
        return Bucket.builder().addLimit(limit).build();
    }

    private String clientIp(HttpServletRequest request) {
        // nginx is the only public entrypoint (see nginx/conf.d/) and always sets this;
        // falls back to the raw remote address for requests that skip the proxy (local/dev).
        String realIp = request.getHeader("X-Real-IP");
        return StringUtils.hasText(realIp) ? realIp : request.getRemoteAddr();
    }

    private void respondTooManyRequests(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(
                new ErrorResponse("요청이 너무 많습니다. 잠시 후 다시 시도해주세요.")));
    }
}
