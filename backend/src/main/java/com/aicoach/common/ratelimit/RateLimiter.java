package com.aicoach.common.ratelimit;

import com.aicoach.common.config.AppProperties;

/** Strategy: decides whether a request identified by {@code key} may proceed under the given bucket. */
public interface RateLimiter {

    RateLimitDecision tryConsume(String key, AppProperties.Bucket bucket);

    record RateLimitDecision(boolean allowed, long limit, long remaining, long retryAfterMillis, long resetMillis) {
    }
}
