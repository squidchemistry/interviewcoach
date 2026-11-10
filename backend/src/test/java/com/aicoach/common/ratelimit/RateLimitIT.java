package com.aicoach.common.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aicoach.common.config.AppProperties;
import com.aicoach.common.ratelimit.RateLimiter.RateLimitDecision;
import com.aicoach.support.AuthTestClient;
import com.aicoach.support.IntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@IntegrationTest
@TestPropertySource(properties = {
        "app.rate-limit.auth.capacity=3", "app.rate-limit.auth.period=1m",
        "app.rate-limit.api.capacity=4", "app.rate-limit.api.period=1m"})
class RateLimitIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    RedisTokenBucketRateLimiter limiter;

    private final ObjectMapper json = new ObjectMapper();

    private static RequestPostProcessor fromIp(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    private static String randomIp() {
        return "10.1." + (int) (Math.random() * 250) + "." + (int) (Math.random() * 250);
    }

    @Test
    void authEndpointsAreLimitedPerClientIp() throws Exception {
        String ip = randomIp();
        String body = "{\"email\":\"nobody@example.com\",\"password\":\"Whatever123\"}";
        for (int remaining = 2; remaining >= 0; remaining--) {
            mvc.perform(post("/api/v1/auth/login").with(fromIp(ip)).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string("RateLimit-Limit", "3"))
                    .andExpect(header().string("RateLimit-Remaining", Integer.toString(remaining)));
        }
        mvc.perform(post("/api/v1/auth/login").with(fromIp(ip)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
        // A different client is unaffected.
        mvc.perform(post("/api/v1/auth/login").with(fromIp(randomIp())).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void apiCallsAreLimitedPerUserNotPerIp() throws Exception {
        String sharedIp = randomIp();
        String alice = token(sharedIp);
        String bob = token(randomIp());
        for (int i = 0; i < 4; i++) {
            mvc.perform(get("/api/v1/me").with(fromIp(sharedIp)).header(HttpHeaders.AUTHORIZATION, alice))
                    .andExpect(status().isOk());
        }
        mvc.perform(get("/api/v1/me").with(fromIp(sharedIp)).header(HttpHeaders.AUTHORIZATION, alice))
                .andExpect(status().isTooManyRequests());
        mvc.perform(get("/api/v1/me").with(fromIp(sharedIp)).header(HttpHeaders.AUTHORIZATION, bob))
                .andExpect(status().isOk());
    }

    @Test
    void healthChecksAreNeverLimited() throws Exception {
        for (int i = 0; i < 10; i++) {
            mvc.perform(get("/api/v1/health").with(fromIp("10.9.9.9")))
                    .andExpect(status().isOk())
                    .andExpect(header().doesNotExist("RateLimit-Limit"));
        }
    }

    @Test
    void bucketRefillsContinuously() throws Exception {
        AppProperties.Bucket bucket = new AppProperties.Bucket(2, Duration.ofMillis(400));
        String key = "test:" + UUID.randomUUID();
        assertThat(limiter.tryConsume(key, bucket).allowed()).isTrue();
        assertThat(limiter.tryConsume(key, bucket).allowed()).isTrue();
        RateLimitDecision denied = limiter.tryConsume(key, bucket);
        assertThat(denied.allowed()).isFalse();
        assertThat(denied.remaining()).isZero();
        assertThat(denied.retryAfterMillis()).isBetween(1L, 200L);
        Thread.sleep(250);
        assertThat(limiter.tryConsume(key, bucket).allowed()).isTrue();
    }

    private String token(String ip) throws Exception {
        String email = AuthTestClient.uniqueEmail();
        String response = mvc.perform(post("/api/v1/auth/register").with(fromIp(ip)).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", AuthTestClient.PASSWORD,
                                "name", "Rate Limited"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return "Bearer " + json.readTree(response).get("accessToken").asText();
    }
}
