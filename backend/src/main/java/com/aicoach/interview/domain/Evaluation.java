package com.aicoach.interview.domain;

import com.aicoach.interview.domain.InterviewEnums.EvaluationStatus;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

/** The final, rubric-based evaluation of one session. */
@Entity
@Table(name = "evaluations")
public class Evaluation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID sessionId;

    @Column(nullable = false)
    private UUID rubricId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EvaluationStatus status = EvaluationStatus.PENDING;

    private BigDecimal overallScore;

    private String confidence;

    private String summary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private JsonNode strengths;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private JsonNode gaps;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private JsonNode nextSteps;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private JsonNode perQuestion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private JsonNode integrity;

    private String provider;

    private String model;

    private String promptVersion;

    @Column(nullable = false)
    private short attempts;

    private String error;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    private Instant completedAt;

    protected Evaluation() {
    }

    public Evaluation(UUID sessionId, UUID rubricId) {
        this.sessionId = sessionId;
        this.rubricId = rubricId;
    }

    public void start() {
        this.status = EvaluationStatus.RUNNING;
        this.attempts++;
        this.error = null;
    }

    public void fail(String error) {
        this.status = EvaluationStatus.FAILED;
        this.error = error;
    }

    public void reset() {
        this.status = EvaluationStatus.PENDING;
        this.error = null;
    }

    public void succeed(BigDecimal overallScore, String confidence, String summary, JsonNode strengths, JsonNode gaps,
            JsonNode nextSteps, JsonNode perQuestion, JsonNode integrity, String provider, String model,
            String promptVersion, Instant now) {
        this.status = EvaluationStatus.SUCCEEDED;
        this.overallScore = overallScore;
        this.confidence = confidence;
        this.summary = summary;
        this.strengths = strengths;
        this.gaps = gaps;
        this.nextSteps = nextSteps;
        this.perQuestion = perQuestion;
        this.integrity = integrity;
        this.provider = provider;
        this.model = model;
        this.promptVersion = promptVersion;
        this.completedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public UUID getRubricId() {
        return rubricId;
    }

    public EvaluationStatus getStatus() {
        return status;
    }

    public BigDecimal getOverallScore() {
        return overallScore;
    }

    public String getConfidence() {
        return confidence;
    }

    public String getSummary() {
        return summary;
    }

    public JsonNode getStrengths() {
        return strengths;
    }

    public JsonNode getGaps() {
        return gaps;
    }

    public JsonNode getNextSteps() {
        return nextSteps;
    }

    public JsonNode getPerQuestion() {
        return perQuestion;
    }

    public JsonNode getIntegrity() {
        return integrity;
    }

    public String getProvider() {
        return provider;
    }

    public String getModel() {
        return model;
    }

    public String getPromptVersion() {
        return promptVersion;
    }

    public int getAttempts() {
        return attempts;
    }

    public String getError() {
        return error;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
