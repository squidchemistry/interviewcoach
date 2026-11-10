package com.aicoach.interview.domain;

import com.aicoach.interview.domain.InterviewEnums.EndReason;
import com.aicoach.interview.domain.InterviewEnums.InterviewMode;
import com.aicoach.interview.domain.InterviewEnums.SessionStage;
import com.aicoach.interview.domain.InterviewEnums.SessionStatus;
import com.aicoach.question.domain.QuestionEnums.Difficulty;
import com.aicoach.user.domain.TargetRole;
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
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

/**
 * Aggregate root of an interview. {@code status}/{@code stage} encode the hierarchical state machine; {@code processing}
 * is true while an asynchronous step (AI generation, assessment) is in flight, which blocks conflicting commands.
 * {@code version} gives optimistic locking: concurrent commands on one session cannot both succeed.
 */
@Entity
@Table(name = "interview_sessions")
public class InterviewSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InterviewMode mode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SessionStatus status = SessionStatus.CREATED;

    @Enumerated(EnumType.STRING)
    private SessionStage stage;

    @Column(nullable = false)
    private boolean processing;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TargetRole targetRole;

    private String company;

    private String packSlug;

    private UUID resumeId;

    @Column(nullable = false)
    private UUID rubricId;

    @Column(nullable = false)
    private boolean voiceEnabled;

    @Column(nullable = false)
    private short plannedQuestions;

    @Column(nullable = false)
    private short currentIndex = -1;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Difficulty currentDifficulty;

    @Column(nullable = false)
    private long seed;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private JsonNode integrity = JsonNodeFactory.instance.arrayNode();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private JsonNode plan = JsonNodeFactory.instance.objectNode();

    @Enumerated(EnumType.STRING)
    private EndReason endReason;

    @Version
    private long version;

    private Instant startedAt;

    @Column(nullable = false)
    private Instant lastActivityAt = Instant.now();

    private Instant endedAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    protected InterviewSession() {
    }

    public InterviewSession(UUID userId, InterviewMode mode, TargetRole targetRole, String company, String packSlug,
            UUID resumeId, UUID rubricId, boolean voiceEnabled, int plannedQuestions, Difficulty startDifficulty, long seed) {
        this.userId = userId;
        this.mode = mode;
        this.targetRole = targetRole;
        this.company = company;
        this.packSlug = packSlug;
        this.resumeId = resumeId;
        this.rubricId = rubricId;
        this.voiceEnabled = voiceEnabled;
        this.plannedQuestions = (short) plannedQuestions;
        this.currentDifficulty = startDifficulty;
        this.seed = seed;
    }

    // ---------------------------------------------------------------- state changes (called by states only)

    public void enter(SessionStatus status, SessionStage stage, boolean processing, Instant now) {
        this.status = status;
        this.stage = stage;
        this.processing = processing;
        this.lastActivityAt = now;
        if (status == SessionStatus.IN_PROGRESS && startedAt == null) {
            startedAt = now;
        }
        if ((status == SessionStatus.EVALUATING || status == SessionStatus.ABANDONED) && endedAt == null) {
            endedAt = now;
        }
    }

    public void setProcessing(boolean processing, Instant now) {
        this.processing = processing;
        this.lastActivityAt = now;
    }

    public void advanceTo(int index, Difficulty difficulty) {
        this.currentIndex = (short) index;
        this.currentDifficulty = difficulty;
    }

    public void end(EndReason reason) {
        this.endReason = reason;
    }

    public void setIntegrity(JsonNode integrity) {
        this.integrity = integrity;
    }

    public void setPlan(JsonNode plan) {
        this.plan = plan;
    }

    public boolean isTerminal() {
        return status == SessionStatus.COMPLETED || status == SessionStatus.ABANDONED;
    }

    // ---------------------------------------------------------------- getters

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public InterviewMode getMode() {
        return mode;
    }

    public SessionStatus getStatus() {
        return status;
    }

    public SessionStage getStage() {
        return stage;
    }

    public boolean isProcessing() {
        return processing;
    }

    public TargetRole getTargetRole() {
        return targetRole;
    }

    public String getCompany() {
        return company;
    }

    public String getPackSlug() {
        return packSlug;
    }

    public UUID getResumeId() {
        return resumeId;
    }

    public UUID getRubricId() {
        return rubricId;
    }

    public boolean isVoiceEnabled() {
        return voiceEnabled;
    }

    public int getPlannedQuestions() {
        return plannedQuestions;
    }

    public int getCurrentIndex() {
        return currentIndex;
    }

    public Difficulty getCurrentDifficulty() {
        return currentDifficulty;
    }

    public long getSeed() {
        return seed;
    }

    public JsonNode getIntegrity() {
        return integrity;
    }

    public JsonNode getPlan() {
        return plan;
    }

    public EndReason getEndReason() {
        return endReason;
    }

    public long getVersion() {
        return version;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getLastActivityAt() {
        return lastActivityAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
