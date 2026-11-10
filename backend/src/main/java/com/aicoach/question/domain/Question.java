package com.aicoach.question.domain;

import com.aicoach.question.domain.QuestionEnums.Difficulty;
import com.aicoach.question.domain.QuestionEnums.QuestionStatus;
import com.aicoach.question.domain.QuestionEnums.QuestionType;
import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

/**
 * A bank question. Common fields are columns; type-specific details are a validated JSON document. The embedding
 * column is managed with native SQL by {@code QuestionEmbeddingRepository} and deliberately not mapped here.
 */
@Entity
@Table(name = "questions")
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QuestionType type;

    @Column(nullable = false)
    private String slug;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String bodyMd;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Difficulty difficulty;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QuestionStatus status = QuestionStatus.PUBLISHED;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false, columnDefinition = "text[]")
    private List<String> tags = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false, columnDefinition = "text[]")
    private List<String> companies = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private JsonNode detail;

    @Column(nullable = false)
    private String source = "ADMIN";

    private UUID createdBy;

    @Version
    private long version;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    protected Question() {
    }

    public static Question create(QuestionType type, String slug, String source, UUID createdBy) {
        Question question = new Question();
        question.type = type;
        question.slug = slug;
        question.source = source;
        question.createdBy = createdBy;
        return question;
    }

    public void update(String title, String bodyMd, Difficulty difficulty, QuestionStatus status, List<String> tags,
            List<String> companies, JsonNode detail) {
        this.title = title;
        this.bodyMd = bodyMd;
        this.difficulty = difficulty;
        this.status = status;
        this.tags = new ArrayList<>(tags);
        this.companies = new ArrayList<>(companies);
        this.detail = detail;
    }

    public void changeStatus(QuestionStatus status) {
        this.status = status;
    }

    /** Text used for the embedding: title, tags and statement. */
    public String embeddingText() {
        return title + "\n" + String.join(", ", tags) + "\n" + bodyMd;
    }

    public UUID getId() {
        return id;
    }

    public QuestionType getType() {
        return type;
    }

    public String getSlug() {
        return slug;
    }

    public String getTitle() {
        return title;
    }

    public String getBodyMd() {
        return bodyMd;
    }

    public Difficulty getDifficulty() {
        return difficulty;
    }

    public QuestionStatus getStatus() {
        return status;
    }

    public List<String> getTags() {
        return tags;
    }

    public List<String> getCompanies() {
        return companies;
    }

    public JsonNode getDetail() {
        return detail;
    }

    public String getSource() {
        return source;
    }

    public long getVersion() {
        return version;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
