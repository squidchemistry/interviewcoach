package com.aicoach.interview.dto;

import com.aicoach.interview.domain.InterviewEnums.EndReason;
import com.aicoach.interview.domain.InterviewEnums.InputMode;
import com.aicoach.interview.domain.InterviewEnums.InterviewMode;
import com.aicoach.interview.domain.InterviewEnums.SessionStage;
import com.aicoach.interview.domain.InterviewEnums.SessionStatus;
import com.aicoach.question.domain.QuestionEnums.Difficulty;
import com.aicoach.user.domain.CodeLanguage;
import com.aicoach.user.domain.TargetRole;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Request/response bodies of the interview API ({@code /api/v1/sessions}). */
public final class InterviewDtos {

    private InterviewDtos() {
    }

    // ------------------------------------------------------------------ requests

    /**
     * {@code questionCount}: DSA/LLD 1-3 (default 2); BEHAVIOURAL/RESUME_DEEP_DIVE 3-6 (default 4).
     * {@code startDifficulty} defaults from the role (SDE_1 → EASY, SDE_2 → MEDIUM). {@code resumeId} defaults to the
     * active resume for RESUME_DEEP_DIVE. {@code packSlug} and {@code voice} may require Pro.
     */
    public record CreateSessionRequest(
            @NotNull InterviewMode mode,
            @Min(1) @Max(6) Integer questionCount,
            Difficulty startDifficulty,
            TargetRole targetRole,
            @Size(max = 40) @Pattern(regexp = "^[A-Za-z0-9 .&-]*$") String company,
            @Size(max = 80) @Pattern(regexp = "^[a-z0-9-]*$") String packSlug,
            UUID resumeId,
            Boolean voice) {
    }

    public record CodeAttachment(@NotNull CodeLanguage language, @NotBlank @Size(max = 65_536) String source) {
    }

    public record DiagramAttachment(@NotBlank @Size(max = 10_240) String mermaid) {
    }

    public record SubmitAnswerRequest(
            @NotNull UUID clientMessageId,
            @NotBlank @Size(max = 8_000) String content,
            InputMode inputMode,
            @Valid CodeAttachment code,
            @Valid DiagramAttachment diagram) {
    }

    public record HintRequest(@Min(1) @Max(3) int level) {
    }

    public record CodeRunRequest(@NotNull CodeLanguage language, @NotBlank @Size(max = 65_536) String source) {
    }

    public record TelemetryEvent(@NotNull @Pattern(regexp = "PASTE|TAB_HIDDEN|TAB_VISIBLE") String type,
            @NotNull Instant at, @Min(0) @Max(10_000_000) Integer size) {
    }

    public record TelemetryRequest(@NotNull @Size(max = 50) List<@Valid TelemetryEvent> events) {
    }

    public record ShareRequest(@Min(1) @Max(30) int expiresInDays) {
    }

    // ------------------------------------------------------------------ responses

    public record SessionSummaryResponse(UUID id, InterviewMode mode, SessionStatus status, SessionStage stage,
            boolean processing, TargetRole targetRole, String company, String packSlug, int plannedQuestions,
            int currentIndex, Difficulty currentDifficulty, boolean voiceEnabled, EndReason endReason,
            Double overallScore, Instant createdAt, Instant startedAt, Instant endedAt) {
    }

    public record TurnResponse(UUID id, int seq, String role, String kind, String content, String format,
            String inputMode, UUID sessionQuestionId, JsonNode metadata, Instant createdAt) {
    }

    /** {@code context}: what the candidate's UI needs (DSA: signature, starter code per language, sample tests). */
    public record SessionQuestionResponse(UUID id, int position, String title, String promptMd, Difficulty difficulty,
            String difficultyReason, List<String> tags, JsonNode context, int hintsUsed, int followUps, Double turnScore,
            boolean completed) {
    }

    /** Full state for (re)loading the interview room; {@code lastEventId} is where the SSE stream should resume. */
    public record SessionDetailResponse(SessionSummaryResponse session, List<SessionQuestionResponse> questions,
            List<TurnResponse> transcript, String lastEventId, String evaluationStatus, boolean codeExecutionEnabled) {
    }

    public record CommandResponse(SessionStatus status, SessionStage stage, boolean processing, String lastEventId) {
    }

    public record TestResultResponse(int index, boolean sample, String verdict, JsonNode input, JsonNode expected,
            String actual, String stdout, String stderr, Integer timeMs) {
    }

    /** {@code executed=false}: the server runs CODE_EXECUTOR=mock, so correctness was not measured. */
    public record CodeRunResponse(UUID submissionId, String kind, String verdict, int passed, int total, Integer runtimeMs,
            String executor, boolean executed, String compileOutput, List<TestResultResponse> tests) {
    }

    public record EvidenceResponse(String quote, int turnSeq) {
    }

    public record CompetencyResponse(String competency, String label, double score, double weight, boolean lowConfidence,
            String rationale, List<EvidenceResponse> evidence, List<String> adjustments) {
    }

    public record RubricRef(String key, int version) {
    }

    public record EvaluatorRef(String provider, String model, String promptVersion) {
    }

    /**
     * {@code status}: PENDING | RUNNING | SUCCEEDED | FAILED. Score fields are null until SUCCEEDED.
     * {@code perQuestion[]}: position, title, difficulty, difficultyReason, tags, hintsUsed, followUps, turnScore,
     * code{verdict,passed,total,executed,language}?, modelAnswerMd, keyMissedPoints[], star{situation,task,action,result:
     * {score,justification,quote}}?. {@code nextSteps[]}: action, why, resourceType, relatedTag.
     */
    public record EvaluationResponse(String status, int attempts, String error, Double overallScore, String confidence,
            String summary, List<CompetencyResponse> competencies, JsonNode strengths, JsonNode gaps, JsonNode nextSteps,
            JsonNode perQuestion, JsonNode integrity, RubricRef rubric, EvaluatorRef evaluator, Instant completedAt) {
    }

    public record ReportResponse(SessionSummaryResponse session, EvaluationResponse evaluation,
            List<TurnResponse> transcript, List<ShareLinkResponse> shareLinks) {
    }

    /** {@code url} (with the token) is returned only on creation. */
    public record ShareLinkResponse(UUID id, String url, Instant expiresAt, Instant revokedAt, int viewCount,
            Instant createdAt) {
    }

    /** Public, anonymised report: first name only, no transcript, evidence, integrity or resume data. */
    public record PublicReportResponse(String candidateFirstName, InterviewMode mode, TargetRole targetRole,
            String company, Instant completedAt, Double overallScore, String confidence, String summary,
            List<PublicCompetency> competencies, JsonNode strengths, JsonNode gaps, List<PublicQuestion> questions) {
    }

    public record PublicCompetency(String competency, String label, double score, boolean lowConfidence) {
    }

    public record PublicQuestion(int position, String title, String difficulty, Double turnScore) {
    }
}
