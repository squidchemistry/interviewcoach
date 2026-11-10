package com.aicoach.progress.dto;

import com.aicoach.interview.dto.InterviewDtos.SessionSummaryResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Dashboard, usage and revision-queue bodies ({@code /api/v1/me/...}). */
public final class ProgressDtos {

    private ProgressDtos() {
    }

    /** {@code interviewLimit} is null for Pro (unlimited). */
    public record UsageResponse(String period, String plan, int interviewsStarted, Integer interviewLimit,
            long tokensUsedToday) {
    }

    public record Totals(int interviews, int completed, Double averageScore, Double bestScore, int questionsAnswered) {
    }

    public record ModeSummary(String mode, int completed, Double averageScore) {
    }

    /** One competency score of one completed session (for trend lines). */
    public record TrendPoint(UUID sessionId, String mode, String competency, double score, Instant at) {
    }

    public record SessionScore(UUID sessionId, String mode, double overallScore, Instant at) {
    }

    public record TopicResponse(String tag, int attempts, double averageScore, double lastScore, Instant dueAt,
            int intervalDays) {
    }

    public record ProgressResponse(Totals totals, List<ModeSummary> modes, List<SessionScore> scores,
            List<TrendPoint> trends, List<TopicResponse> weakTopics, List<SessionSummaryResponse> recent) {
    }

    public record SuggestedQuestion(UUID id, String slug, String title, String type, String difficulty) {
    }

    /** {@code due}: the topic is due for revision now (SM-2). */
    public record RevisionItem(String tag, Instant dueAt, boolean due, double averageScore, int attempts,
            int intervalDays, List<SuggestedQuestion> suggestedQuestions) {
    }

    public record RevisionResponse(List<RevisionItem> items) {
    }
}
