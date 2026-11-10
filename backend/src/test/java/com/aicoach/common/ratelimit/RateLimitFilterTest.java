package com.aicoach.common.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import com.aicoach.common.config.AppProperties;
import com.aicoach.common.error.ProblemDetailWriter;
import com.aicoach.common.ratelimit.RateLimitFilter.LimitClass;
import com.aicoach.common.ratelimit.RateLimiter.RateLimitDecision;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class RateLimitFilterTest {

    private static final AppProperties.Bucket BUCKET = new AppProperties.Bucket(5, Duration.ofMinutes(1));
    private static final AppProperties.RateLimit CONFIG = new AppProperties.RateLimit(true, BUCKET, BUCKET, BUCKET);
    private final ProblemDetailWriter writer = new ProblemDetailWriter(Jackson2ObjectMapperBuilder.json().build());
    private final List<String> keys = new ArrayList<>();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private RateLimitFilter filter(RateLimitDecision decision) {
        return new RateLimitFilter((key, bucket) -> {
            keys.add(key);
            return decision;
        }, CONFIG, writer);
    }

    @Test
    void classifiesEndpoints() {
        assertThat(RateLimitFilter.classify("POST", "/api/v1/auth/login")).isEqualTo(LimitClass.AUTH);
        assertThat(RateLimitFilter.classify("POST", "/api/v1/auth/register")).isEqualTo(LimitClass.AUTH);
        assertThat(RateLimitFilter.classify("POST", "/api/v1/auth/refresh")).isEqualTo(LimitClass.REFRESH);
        assertThat(RateLimitFilter.classify("GET", "/api/v1/auth/google/callback")).isEqualTo(LimitClass.REFRESH);
        assertThat(RateLimitFilter.classify("GET", "/api/v1/me")).isEqualTo(LimitClass.API);
    }

    @Test
    void allowedRequestsGetRateLimitHeaders() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter(new RateLimitDecision(true, 5, 3, 0, 24_000)).doFilter(new MockHttpServletRequest("GET", "/api/v1/me"),
                response, chain);
        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getHeader("RateLimit-Limit")).isEqualTo("5");
        assertThat(response.getHeader("RateLimit-Remaining")).isEqualTo("3");
        assertThat(response.getHeader("RateLimit-Reset")).isEqualTo("24");
        assertThat(keys).containsExactly("api:ip:127.0.0.1");
    }

    @Test
    void deniedRequestsGet429WithRetryAfterAndNeverReachTheController() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter(new RateLimitDecision(false, 5, 0, 1_500, 12_000)).doFilter(
                new MockHttpServletRequest("POST", "/api/v1/auth/login"), response, chain);
        assertThat(chain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isEqualTo("2");
        assertThat(response.getContentAsString()).contains("\"code\":\"RATE_LIMITED\"");
    }

    @Test
    void authenticatedApiCallsAreKeyedByUser() throws Exception {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "HS256").subject("user-42").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        filter(new RateLimitDecision(true, 5, 4, 0, 0)).doFilter(new MockHttpServletRequest("GET", "/api/v1/me"),
                new MockHttpServletResponse(), new MockFilterChain());
        assertThat(keys).containsExactly("api:user:user-42");
    }

    @Test
    void failsOpenWhenTheLimiterIsUnavailable() throws Exception {
        RateLimitFilter failing = new RateLimitFilter((key, bucket) -> {
            throw new IllegalStateException("redis down");
        }, CONFIG, writer);
        MockFilterChain chain = new MockFilterChain();
        failing.doFilter(new MockHttpServletRequest("GET", "/api/v1/me"), new MockHttpServletResponse(), chain);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void skipsNonApiPathsHealthPreflightsAndDisabledConfig() throws Exception {
        RateLimitFilter filter = filter(new RateLimitDecision(false, 5, 0, 1000, 1000));
        for (MockHttpServletRequest request : List.of(
                new MockHttpServletRequest("GET", "/swagger-ui.html"),
                new MockHttpServletRequest("GET", "/api/v1/health"),
                new MockHttpServletRequest("OPTIONS", "/api/v1/me"))) {
            MockFilterChain chain = new MockFilterChain();
            filter.doFilter(request, new MockHttpServletResponse(), chain);
            assertThat(chain.getRequest()).isNotNull();
        }
        RateLimitFilter disabled = new RateLimitFilter((k, b) -> new RateLimitDecision(false, 1, 0, 1, 1),
                new AppProperties.RateLimit(false, BUCKET, BUCKET, BUCKET), writer);
        MockFilterChain chain = new MockFilterChain();
        disabled.doFilter(new MockHttpServletRequest("GET", "/api/v1/me"), new MockHttpServletResponse(), chain);
        assertThat(chain.getRequest()).isNotNull();
        assertThat(keys).isEmpty();
    }
}
