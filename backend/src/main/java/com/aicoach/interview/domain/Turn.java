package com.aicoach.interview.domain;

import com.aicoach.interview.domain.InterviewEnums.ContentFormat;
import com.aicoach.interview.domain.InterviewEnums.InputMode;
import com.aicoach.interview.domain.InterviewEnums.TurnKind;
import com.aicoach.interview.domain.InterviewEnums.TurnRole;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

/**
 * One message of the transcript. Append-only, ordered by {@code seq}. The id may be assigned before persisting so that
 * streamed tokens and the final stored turn share the same id.
 */
@Entity
@Table(name = "turns")
public class Turn implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID sessionId;

    private UUID sessionQuestionId;

    @Column(nullable = false)
    private int seq;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TurnRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TurnKind kind;

    @Column(nullable = false)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContentFormat format = ContentFormat.MARKDOWN;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InputMode inputMode = InputMode.TEXT;

    private UUID clientMessageId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private JsonNode metadata = JsonNodeFactory.instance.objectNode();

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Transient
    private boolean isNew = true;

    protected Turn() {
    }

    public Turn(UUID id, UUID sessionId, UUID sessionQuestionId, int seq, TurnRole role, TurnKind kind, String content,
            ContentFormat format, InputMode inputMode, UUID clientMessageId, JsonNode metadata) {
        this.id = id;
        this.sessionId = sessionId;
        this.sessionQuestionId = sessionQuestionId;
        this.seq = seq;
        this.role = role;
        this.kind = kind;
        this.content = content;
        this.format = format;
        this.inputMode = inputMode;
        this.clientMessageId = clientMessageId;
        if (metadata != null) {
            this.metadata = metadata;
        }
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.isNew = false;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @Override
    public UUID getId() {
        return id;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public UUID getSessionQuestionId() {
        return sessionQuestionId;
    }

    public int getSeq() {
        return seq;
    }

    public TurnRole getRole() {
        return role;
    }

    public TurnKind getKind() {
        return kind;
    }

    public String getContent() {
        return content;
    }

    public ContentFormat getFormat() {
        return format;
    }

    public InputMode getInputMode() {
        return inputMode;
    }

    public UUID getClientMessageId() {
        return clientMessageId;
    }

    public JsonNode getMetadata() {
        return metadata;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
