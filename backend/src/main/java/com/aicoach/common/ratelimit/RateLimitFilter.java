package com.aicoach.common.ratelimit;

import com.aicoach.common.config.AppProperties;
import com.aicoach.common.error.ErrorCode;
import com.aicoach.common.error.ProblemDetailFactory;
import com.aicoach.common.error.ProblemDetailWriter;
import com.aicoach.common.ratelimit.RateLimiter.RateLimitDecision;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Applies a rate-limit class to every API request and sets IETF {@code RateLimit-*} headers.
 * Runs inside the security chain after bearer-token authentication, so authenticated callers are limited per user
 * (fair behind shared NAT) and anonymous callers per client IP.
 * <p>
 * Fails open: if Redis is unavailable the request proceeds (logged). Readiness already reports Redis DOWN, so the
 * load balancer drains the instance; refusing all traffic here would turn a cache outage into a full outage.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private final RateLimiter limiter;
    private final AppProperties.RateLimit config;
    private final ProblemDetailWriter problemWriter;

    public RateLimitFilter(RateLimiter limiter, AppProperties.RateLimit config, ProblemDetailWriter problemWriter) {
        this.limiter = limiter;
        this.config = config;
        this.problemWriter = problemWriter;
    }

    enum LimitClass { AUTH, REFRESH, API }

    static LimitClass classify(String method, String path) {
        if ("POST".equals(method) && ("/api/v1/auth/login".equals(path) || "/api/v1/auth/register".equals(path))) {
            return LimitClass.AUTH;
        }
        if ("/api/v1/auth/refresh".equals(path) || path.startsWith("/api/v1/auth/google")) {
            return LimitClass.REFRESH;
        }
        return LimitClass.API;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !config.enabled()
                || !path.startsWith("/api/")
                || "OPTIONS".equals(request.getMethod())
                || "/api/v1/health".equals(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        LimitClass limitClass = classify(request.getMethod(), request.getRequestURI());
        AppProperties.Bucket bucket = switch (limitClass) {
            case AUTH -> config.auth();
            case REFRESH -> config.refresh();
            case API -> config.api();
        };
        String key = limitClass.name().toLowerCase() + ":" + subject(request, limitClass);

        RateLimitDecision decision;
        try {
            decision = limiter.tryConsume(key, bucket);
        } catch (RuntimeException e) {
            log.warn("Rate limiter unavailable, allowing request: {}", e.toString());
            chain.doFilter(request, response);
            return;
        }

        response.setHeader("RateLimit-Limit", Long.toString(decision.limit()));
        response.setHeader("RateLimit-Remaining", Long.toString(decision.remaining()));
        response.setHeader("RateLimit-Reset", Long.toString(seconds(decision.resetMillis())));
        if (!decision.allowed()) {
            long retryAfter = Math.max(1, seconds(decision.retryAfterMillis()));
            response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(retryAfter));
            problemWriter.write(response, ProblemDetailFactory.create(ErrorCode.RATE_LIMITED,
                    "Too many requests. Retry after " + retryAfter + " seconds.", request.getRequestURI()));
            return;
        }
        chain.doFilter(request, response);
    }

    /** Per-user for authenticated API calls; per-IP for anonymous calls and the auth endpoints. */
    private static String subject(HttpServletRequest request, LimitClass limitClass) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (limitClass == LimitClass.API && auth instanceof JwtAuthenticationToken jwt) {
            return "user:" + jwt.getName();
        }
        return "ip:" + request.getRemoteAddr();
    }

    private static long seconds(long millis) {
        return (millis + 999) / 1000;
    }
}
