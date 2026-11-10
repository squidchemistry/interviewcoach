package com.aicoach.question.domain;

import com.aicoach.question.domain.QuestionEnums.QuestionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A curated, company-specific set of questions (e.g. Amazon Leadership Principles). */
@Entity
@Table(name = "question_packs")
public class QuestionPack {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String slug;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QuestionType mode;

    private String company;

    @Column(nullable = false)
    private boolean pro;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false, columnDefinition = "text[]")
    private List<String> questionSlugs = new ArrayList<>();

    protected QuestionPack() {
    }

    public QuestionPack(String slug, String name, String description, QuestionType mode, String company, boolean pro,
            List<String> questionSlugs) {
        this.slug = slug;
        this.name = name;
        this.description = description;
        this.mode = mode;
        this.company = company;
        this.pro = pro;
        this.questionSlugs = new ArrayList<>(questionSlugs);
    }

    public UUID getId() {
        return id;
    }

    public String getSlug() {
        return slug;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public QuestionType getMode() {
        return mode;
    }

    public String getCompany() {
        return company;
    }

    public boolean isPro() {
        return pro;
    }

    public List<String> getQuestionSlugs() {
        return questionSlugs;
    }
}
