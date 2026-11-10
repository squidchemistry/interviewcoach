package com.aicoach.resume.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

/**
 * An uploaded resume. The PDF bytes ({@code content}) are deliberately not mapped: they are written once and read only
 * by the parser, via {@code ResumeContentRepository}, so listing resumes never loads megabytes.
 */
@Entity
@Table(name = "resumes")
public class Resume {

    public enum Status { UPLOADED, PARSING, PARSED, FAILED }

    @Id
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, updatable = false)
    private String originalFilename;

    @Column(nullable = false, updatable = false)
    private int sizeBytes;

    @Column(nullable = false, updatable = false)
    private String sha256;

    @Column(nullable = false, updatable = false)
    private int pageCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    private String rawText;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private JsonNode parsed;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private JsonNode integrity = JsonNodeFactory.instance.arrayNode();

    private String error;

    @Column(nullable = false)
    private boolean active;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    protected Resume() {
    }

    public void startParsing() {
        this.status = Status.PARSING;
        this.error = null;
    }

    public void parsed(String rawText, JsonNode parsed, JsonNode integrity) {
        this.status = Status.PARSED;
        this.rawText = rawText;
        this.parsed = parsed;
        this.integrity = integrity;
        this.error = null;
    }

    public void failed(String error) {
        this.status = Status.FAILED;
        this.error = error;
    }

    public void correct(JsonNode parsed) {
        this.parsed = parsed;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public int getSizeBytes() {
        return sizeBytes;
    }

    public String getSha256() {
        return sha256;
    }

    public int getPageCount() {
        return pageCount;
    }

    public Status getStatus() {
        return status;
    }

    public String getRawText() {
        return rawText;
    }

    public JsonNode getParsed() {
        return parsed;
    }

    public JsonNode getIntegrity() {
        return integrity;
    }

    public String getError() {
        return error;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
