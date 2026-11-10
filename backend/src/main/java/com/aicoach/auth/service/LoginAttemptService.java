package com.aicoach.auth.service;

import com.aicoach.common.config.AppProperties;
import com.aicoach.common.error.RateLimitedException;
import com.aicoach.common.security.Hashing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Brute-force protection: after {@code maxFailures} failed logins for the same (email, client IP) within the
 * window, further attempts are refused with 429 until the window expires — even with the right password.
 * Keyed by email+IP so an attacker cannot lock a victim out from everywhere. The email is hashed in the key so
 * Redis never holds personal data in clear text.
 */
@Service
public class LoginAttemptService {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptService.class);
    static final String KEY_PREFIX = "auth:login-failures:";

    private final StringRedisTemplate redis;
    private final AppProperties.Login config;

    public LoginAttemptService(StringRedisTemplate redis, AppProperties properties) {
        this.redis = redis;
        this.config = properties.auth().login();
    }

    public void assertNotLocked(String normalizedEmail, String ip) {
        String key = key(normalizedEmail, ip);
        try {
            String count = redis.opsForValue().get(key);
            if (count != null && Integer.parseInt(count) >= config.maxFailures()) {
                Long ttl = redis.getExpire(key);
                long retryAfter = ttl == null || ttl <= 0 ? config.lockout().toSeconds() : ttl;
                long minutes = Math.max(1, (retryAfter + 59) / 60);
                throw new RateLimitedException("Too many failed sign-in attempts. Try again in " + minutes
                        + (minutes == 1 ? " minute." : " minutes."), retryAfter);
            }
        } catch (DataAccessException e) {
            log.warn("Login attempt store unavailable; skipping lockout check: {}", e.toString());
        }
    }

    public void recordFailure(String normalizedEmail, String ip) {
        String key = key(normalizedEmail, ip);
        try {
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1) {
                redis.expire(key, config.lockout());
            }
        } catch (DataAccessException e) {
            log.warn("Login attempt store unavailable; failure not recorded: {}", e.toString());
        }
    }

    public void reset(String normalizedEmail, String ip) {
        try {
            redis.delete(key(normalizedEmail, ip));
        } catch (DataAccessException e) {
            log.warn("Login attempt store unavailable; reset skipped: {}", e.toString());
        }
    }

    private static String key(String normalizedEmail, String ip) {
        return KEY_PREFIX + Hashing.sha256Hex(normalizedEmail + "|" + ip);
    }
}
