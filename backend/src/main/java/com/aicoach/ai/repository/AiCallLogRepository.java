package com.aicoach.ai.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.aicoach.ai.AiUsageRow;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Append-only metering of AI calls (plain JDBC: write-heavy, no entity lifecycle needed). */
@Repository
public class AiCallLogRepository {

    private final JdbcTemplate jdbc;

    public AiCallLogRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record AiCallLog(String requestId, UUID userId, UUID sessionId, String purpose, String provider, String model,
            String promptId, String promptVersion, int inputTokens, int outputTokens, int latencyMs, boolean cacheHit,
            String status, String errorCode) {
    }

    public void insert(AiCallLog log) {
        jdbc.update("""
                INSERT INTO ai_call_logs (request_id, user_id, session_id, purpose, provider, model, prompt_id, prompt_version,
                                          input_tokens, output_tokens, latency_ms, cache_hit, status, error_code)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, log.requestId(), log.userId(), log.sessionId(), log.purpose(), log.provider(), log.model(),
                log.promptId(), log.promptVersion(), log.inputTokens(), log.outputTokens(), log.latencyMs(),
                log.cacheHit(), log.status(), log.errorCode());
    }

    public long tokensForSession(UUID sessionId) {
        Long sum = jdbc.queryForObject(
                "SELECT COALESCE(SUM(input_tokens + output_tokens), 0) FROM ai_call_logs WHERE session_id = ?",
                Long.class, sessionId);
        return sum == null ? 0 : sum;
    }

    public long tokensForUserSince(UUID userId, Instant since) {
        Long sum = jdbc.queryForObject(
                "SELECT COALESCE(SUM(input_tokens + output_tokens), 0) FROM ai_call_logs WHERE user_id = ? AND created_at >= ?",
                Long.class, userId, java.sql.Timestamp.from(since));
        return sum == null ? 0 : sum;
    }

    /** Aggregates for the admin AI-usage view, grouped by day, purpose or model. */
    public List<AiUsageRow> usage(String groupBy, Instant from, Instant to) {
        String keyExpr = switch (groupBy) {
            case "purpose" -> "purpose";
            case "model" -> "provider || '/' || model";
            default -> "to_char(date_trunc('day', created_at), 'YYYY-MM-DD')";
        };
        return jdbc.query("""
                SELECT %s AS k, count(*) AS calls, sum(input_tokens) AS input_tokens, sum(output_tokens) AS output_tokens,
                       avg(CASE WHEN cache_hit THEN 1.0 ELSE 0.0 END) AS cache_hit_rate,
                       percentile_cont(0.95) WITHIN GROUP (ORDER BY latency_ms) AS p95,
                       avg(CASE WHEN status = 'OK' THEN 0.0 ELSE 1.0 END) AS error_rate
                FROM ai_call_logs WHERE created_at >= ? AND created_at < ?
                GROUP BY k ORDER BY k
                """.formatted(keyExpr), (rs, i) -> new AiUsageRow(rs.getString("k"), rs.getLong("calls"),
                rs.getLong("input_tokens"), rs.getLong("output_tokens"), rs.getDouble("cache_hit_rate"),
                Math.round(rs.getDouble("p95")), rs.getDouble("error_rate")),
                java.sql.Timestamp.from(from), java.sql.Timestamp.from(to));
    }
}
