package com.aicoach.interview.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Plain-SQL access for append-only and aggregate tables (code submissions, competency scores, topic statistics,
 * monthly usage): no entity lifecycle needed, and the dashboard queries are clearer as SQL.
 */
@Repository
public class InterviewJdbcRepository {

    private final JdbcTemplate jdbc;

    public InterviewJdbcRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ---------------------------------------------------------------- code submissions

    public UUID insertCodeSubmission(UUID sessionQuestionId, UUID userId, String kind, String language, String source,
            String verdict, int passed, int total, String resultsJson, Integer runtimeMs, String executor) {
        return jdbc.queryForObject("""
                INSERT INTO code_submissions (session_question_id, user_id, kind, language, source, verdict, passed, total,
                                              results, runtime_ms, executor)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?) RETURNING id
                """, UUID.class, sessionQuestionId, userId, kind, language, source, verdict, passed, total, resultsJson,
                runtimeMs, executor);
    }

    public record CodeSubmissionSummary(UUID sessionQuestionId, String kind, String language, String verdict, int passed,
            int total, String executor, String source) {
    }

    /** Latest submission (prefers SUBMIT over RUN) per session question. */
    public List<CodeSubmissionSummary> latestSubmissions(UUID sessionId) {
        return jdbc.query("""
                SELECT DISTINCT ON (cs.session_question_id) cs.session_question_id, cs.kind, cs.language, cs.verdict,
                       cs.passed, cs.total, cs.executor, cs.source
                FROM code_submissions cs JOIN session_questions sq ON sq.id = cs.session_question_id
                WHERE sq.session_id = ?
                ORDER BY cs.session_question_id, (cs.kind = 'SUBMIT') DESC, cs.created_at DESC
                """, (rs, i) -> new CodeSubmissionSummary(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                rs.getString(4), rs.getInt(5), rs.getInt(6), rs.getString(7), rs.getString(8)), sessionId);
    }

    // ---------------------------------------------------------------- competency scores

    public void insertCompetencyScore(UUID evaluationId, UUID userId, String mode, String competency, double score,
            double weight, boolean lowConfidence, String rationale, String evidenceJson) {
        jdbc.update("""
                INSERT INTO competency_scores (evaluation_id, user_id, mode, competency, score, weight, low_confidence,
                                               rationale, evidence)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb))
                """, evaluationId, userId, mode, competency, score, weight, lowConfidence, rationale, evidenceJson);
    }

    public void deleteCompetencyScores(UUID evaluationId) {
        jdbc.update("DELETE FROM competency_scores WHERE evaluation_id = ?", evaluationId);
    }

    public record CompetencyScoreRow(String competency, double score, double weight, boolean lowConfidence,
            String rationale, String evidenceJson) {
    }

    public List<CompetencyScoreRow> competencyScores(UUID evaluationId) {
        return jdbc.query("""
                SELECT competency, score, weight, low_confidence, rationale, evidence::text FROM competency_scores
                WHERE evaluation_id = ? ORDER BY weight DESC, competency
                """, (rs, i) -> new CompetencyScoreRow(rs.getString(1), rs.getDouble(2), rs.getDouble(3),
                rs.getBoolean(4), rs.getString(5), rs.getString(6)), evaluationId);
    }

    public record TrendPoint(UUID sessionId, String mode, String competency, double score, Instant at) {
    }

    public List<TrendPoint> trends(UUID userId, String mode, int sessions) {
        return jdbc.query("""
                WITH recent AS (
                    SELECT s.id FROM interview_sessions s
                    WHERE s.user_id = ? AND s.status = 'COMPLETED' AND (CAST(? AS text) IS NULL OR s.mode = ?)
                    ORDER BY s.ended_at DESC NULLS LAST LIMIT ?
                )
                SELECT e.session_id, cs.mode, cs.competency, cs.score, e.completed_at
                FROM competency_scores cs JOIN evaluations e ON e.id = cs.evaluation_id
                WHERE e.session_id IN (SELECT id FROM recent)
                ORDER BY e.completed_at, cs.competency
                """, (rs, i) -> new TrendPoint(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                rs.getDouble(4), rs.getTimestamp(5).toInstant()), userId, mode, mode, sessions);
    }

    public record ModeSummary(String mode, int count, Double avgOverall) {
    }

    public List<ModeSummary> modeSummaries(UUID userId) {
        return jdbc.query("""
                SELECT s.mode, count(*) AS n, avg(e.overall_score) AS avg_overall
                FROM interview_sessions s LEFT JOIN evaluations e ON e.session_id = s.id AND e.status = 'SUCCEEDED'
                WHERE s.user_id = ? AND s.status = 'COMPLETED'
                GROUP BY s.mode ORDER BY s.mode
                """, (rs, i) -> new ModeSummary(rs.getString(1), rs.getInt(2),
                rs.getObject(3) == null ? null : rs.getDouble(3)), userId);
    }

    public Map<String, Object> totals(UUID userId) {
        return jdbc.queryForMap("""
                SELECT count(*) AS sessions,
                       count(*) FILTER (WHERE s.status = 'COMPLETED') AS completed,
                       avg(e.overall_score) AS avg_overall,
                       max(e.overall_score) AS best_overall,
                       (SELECT count(*) FROM session_questions sq JOIN interview_sessions s2 ON s2.id = sq.session_id
                        WHERE s2.user_id = ? AND sq.turn_score IS NOT NULL) AS answered
                FROM interview_sessions s LEFT JOIN evaluations e ON e.session_id = s.id AND e.status = 'SUCCEEDED'
                WHERE s.user_id = ?
                """, userId, userId);
    }

    public record SessionScore(UUID sessionId, String mode, double overallScore, Instant at) {
    }

    /** Overall scores of completed sessions, oldest first (the dashboard's score-over-time line). */
    public List<SessionScore> sessionScores(UUID userId, String mode, int limit) {
        return jdbc.query("""
                SELECT * FROM (
                    SELECT s.id, s.mode, e.overall_score, e.completed_at
                    FROM interview_sessions s JOIN evaluations e ON e.session_id = s.id AND e.status = 'SUCCEEDED'
                    WHERE s.user_id = ? AND (CAST(? AS text) IS NULL OR s.mode = ?)
                    ORDER BY e.completed_at DESC LIMIT ?
                ) t ORDER BY completed_at
                """, (rs, i) -> new SessionScore(rs.getObject(1, UUID.class), rs.getString(2), rs.getDouble(3),
                rs.getTimestamp(4).toInstant()), userId, mode, mode, limit);
    }

    // ---------------------------------------------------------------- topic statistics + spaced repetition (SM-2)

    public record TopicStat(String tag, int attempts, double avgScore, double lastScore, int repetitions, double ease,
            int intervalDays, Instant dueAt, Instant lastSeenAt) {
    }

    public java.util.Optional<TopicStat> topic(UUID userId, String tag) {
        return jdbc.query("""
                SELECT tag, attempts, avg_score, last_score, sr_repetitions, sr_ease, sr_interval_days, sr_due_at, last_seen_at
                FROM topic_stats WHERE user_id = ? AND tag = ?
                """, (rs, i) -> topicRow(rs), userId, tag).stream().findFirst();
    }

    public void upsertTopic(UUID userId, TopicStat s) {
        jdbc.update("""
                INSERT INTO topic_stats (user_id, tag, attempts, avg_score, last_score, last_seen_at, sr_repetitions, sr_ease,
                                         sr_interval_days, sr_due_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (user_id, tag) DO UPDATE SET attempts = EXCLUDED.attempts, avg_score = EXCLUDED.avg_score,
                    last_score = EXCLUDED.last_score, last_seen_at = EXCLUDED.last_seen_at,
                    sr_repetitions = EXCLUDED.sr_repetitions, sr_ease = EXCLUDED.sr_ease,
                    sr_interval_days = EXCLUDED.sr_interval_days, sr_due_at = EXCLUDED.sr_due_at
                """, userId, s.tag(), s.attempts(), s.avgScore(), s.lastScore(), Timestamp.from(s.lastSeenAt()),
                s.repetitions(), s.ease(), s.intervalDays(), Timestamp.from(s.dueAt()));
    }

    public List<TopicStat> weakTopics(UUID userId, int limit) {
        return jdbc.query("""
                SELECT tag, attempts, avg_score, last_score, sr_repetitions, sr_ease, sr_interval_days, sr_due_at, last_seen_at
                FROM topic_stats WHERE user_id = ? AND attempts >= 1 ORDER BY avg_score, attempts DESC LIMIT ?
                """, (rs, i) -> topicRow(rs), userId, limit);
    }

    public List<TopicStat> dueTopics(UUID userId, Instant dueBefore) {
        return jdbc.query("""
                SELECT tag, attempts, avg_score, last_score, sr_repetitions, sr_ease, sr_interval_days, sr_due_at, last_seen_at
                FROM topic_stats WHERE user_id = ? ORDER BY (sr_due_at <= ?) DESC, sr_due_at, avg_score
                """, (rs, i) -> topicRow(rs), userId, Timestamp.from(dueBefore));
    }

    private static TopicStat topicRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new TopicStat(rs.getString("tag"), rs.getInt("attempts"), rs.getDouble("avg_score"),
                rs.getDouble("last_score"), rs.getInt("sr_repetitions"), rs.getDouble("sr_ease"),
                rs.getInt("sr_interval_days"), rs.getTimestamp("sr_due_at").toInstant(),
                rs.getTimestamp("last_seen_at").toInstant());
    }

    // ---------------------------------------------------------------- monthly usage (free-plan quota)

    public int interviewsStarted(UUID userId, String period) {
        Integer n = jdbc.query("SELECT interviews_started FROM usage_monthly WHERE user_id = ? AND period = ?",
                (rs, i) -> rs.getInt(1), userId, period).stream().findFirst().orElse(0);
        return n;
    }

    public void incrementUsage(UUID userId, String period) {
        jdbc.update("""
                INSERT INTO usage_monthly (user_id, period, interviews_started) VALUES (?, ?, 1)
                ON CONFLICT (user_id, period) DO UPDATE SET interviews_started = usage_monthly.interviews_started + 1
                """, userId, period);
    }
}
