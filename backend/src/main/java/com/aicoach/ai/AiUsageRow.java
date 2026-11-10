package com.aicoach.ai;

/** One aggregated row of AI usage (by day, purpose or model) for the admin dashboard. */
public record AiUsageRow(String key, long calls, long inputTokens, long outputTokens, double cacheHitRate,
        long p95LatencyMs, double errorRate) {
}
