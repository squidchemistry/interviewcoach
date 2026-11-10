package com.aicoach.question.domain;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * An immutable, versioned scoring rubric for one interview mode. Sessions pin the rubric version they started with,
 * so old reports stay reproducible after a rubric changes.
 */
@Entity
@Table(name = "rubrics")
public class Rubric {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String key;

    @Column(nullable = false)
    private int version;

    @Column(nullable = false)
    private boolean active;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private JsonNode definition;

    @Column(nullable = false)
    private String changelog = "";

    private UUID createdBy;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Rubric() {
    }

    public Rubric(String key, int version, JsonNode definition, String changelog, UUID createdBy) {
        this.key = key;
        this.version = version;
        this.definition = definition;
        this.changelog = changelog;
        this.createdBy = createdBy;
    }

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }

    public UUID getId() {
        return id;
    }

    public String getKey() {
        return key;
    }

    public int getVersion() {
        return version;
    }

    public boolean isActive() {
        return active;
    }

    public JsonNode getDefinition() {
        return definition;
    }

    public String getChangelog() {
        return changelog;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
