package com.aicoach.interview.domain;

import com.aicoach.question.domain.QuestionEnums.Difficulty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A question as it was asked in one session (a snapshot: later edits to the bank don't change history). */
@Entity
@Table(name = "session_questions")
public class SessionQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID sessionId;

    @Column(nullable = false)
    private short position;

    private UUID questionId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String promptMd;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Difficulty difficulty;

    @Column(nullable = false)
    private String difficultyReason;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false, columnDefinition = "text[]")
    private List<String> tags = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private JsonNode context = JsonNodeFactory.instance.objectNode();

    @Column(nullable = false)
    private short hintsUsed;

    @Column(nullable = false)
    private short followUps;

    private BigDecimal turnScore;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private JsonNode assessment;

    @Column(nullable = false)
    private Instant startedAt = Instant.now();

    private Instant completedAt;

    protected SessionQuestion() {
    }

    public SessionQuestion(UUID sessionId, int position, UUID questionId, String title, String promptMd,
            Difficulty difficulty, String difficultyReason, List<String> tags, JsonNode context) {
        this.sessionId = sessionId;
        this.position = (short) position;
        this.questionId = questionId;
        this.title = title;
        this.promptMd = promptMd;
        this.difficulty = difficulty;
        this.difficultyReason = difficultyReason;
        this.tags = new ArrayList<>(tags);
        this.context = context;
    }

    public void recordAssessment(double score, JsonNode assessment) {
        this.turnScore = BigDecimal.valueOf(score).setScale(2, RoundingMode.HALF_UP);
        this.assessment = assessment;
    }

    public void incrementFollowUps() {
        this.followUps++;
    }

    public void useHint(int level) {
        this.hintsUsed = (short) level;
    }

    public void complete(Instant now) {
        if (completedAt == null) {
            completedAt = now;
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public int getPosition() {
        return position;
    }

    public UUID getQuestionId() {
        return questionId;
    }

    public String getTitle() {
        return title;
    }

    public String getPromptMd() {
        return promptMd;
    }

    public Difficulty getDifficulty() {
        return difficulty;
    }

    public String getDifficultyReason() {
        return difficultyReason;
    }

    public List<String> getTags() {
        return tags;
    }

    public JsonNode getContext() {
        return context;
    }

    public int getHintsUsed() {
        return hintsUsed;
    }

    public int getFollowUps() {
        return followUps;
    }

    public BigDecimal getTurnScore() {
        return turnScore;
    }

    public JsonNode getAssessment() {
        return assessment;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
