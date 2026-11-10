package com.aicoach.common.ratelimit;

import com.aicoach.common.config.AppProperties;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * Distributed token bucket (Strategy implementation of {@link RateLimiter}). State lives in Redis so limits hold
 * across all API replicas; the Lua script makes read-modify-write atomic and uses the Redis server clock.
 */
@Component
public class RedisTokenBucketRateLimiter implements RateLimiter {

    static final String KEY_PREFIX = "ratelimit:";

    @SuppressWarnings("rawtypes")
    private static final RedisScript<List> SCRIPT =
            RedisScript.of(new ClassPathResource("redis/token_bucket.lua"), List.class);

    private final StringRedisTemplate redis;

    public RedisTokenBucketRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public RateLimitDecision tryConsume(String key, AppProperties.Bucket bucket) {
        List<?> result = redis.execute(SCRIPT, List.of(KEY_PREFIX + key),
                Integer.toString(bucket.capacity()), Long.toString(bucket.period().toMillis()), "1");
        if (result == null || result.size() != 4) {
            throw new IllegalStateException("Unexpected token bucket script result: " + result);
        }
        return new RateLimitDecision(
                number(result.get(0)) == 1, bucket.capacity(), number(result.get(1)),
                number(result.get(2)), number(result.get(3)));
    }

    private static long number(Object value) {
        return ((Number) value).longValue();
    }
}
