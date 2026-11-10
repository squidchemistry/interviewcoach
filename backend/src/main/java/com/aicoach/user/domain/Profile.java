package com.aicoach.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.data.domain.Persistable;

/** Interview goals of a candidate. Shares its primary key with {@link User}. */
@Entity
@Table(name = "profiles")
public class Profile implements Persistable<UUID> {

    @Id
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TargetRole targetRole;

    @Column(nullable = false, precision = 3, scale = 1)
    private BigDecimal experienceYears;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CodeLanguage preferredLanguage;

    @ManyToMany
    @JoinTable(name = "user_target_companies",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "company_id"))
    private Set<Company> targetCompanies = new LinkedHashSet<>();

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant onboardedAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    @Transient
    private boolean isNew = true;

    protected Profile() {
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.isNew = false;
    }

    @Override
    public UUID getId() {
        return userId;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    public Profile(UUID userId) {
        this.userId = userId;
    }

    public void update(TargetRole targetRole, BigDecimal experienceYears, CodeLanguage preferredLanguage,
            Set<Company> companies) {
        this.targetRole = targetRole;
        this.experienceYears = experienceYears;
        this.preferredLanguage = preferredLanguage;
        this.targetCompanies.clear();
        this.targetCompanies.addAll(companies);
    }

    public UUID getUserId() {
        return userId;
    }

    public TargetRole getTargetRole() {
        return targetRole;
    }

    public BigDecimal getExperienceYears() {
        return experienceYears;
    }

    public CodeLanguage getPreferredLanguage() {
        return preferredLanguage;
    }

    public Set<Company> getTargetCompanies() {
        return targetCompanies;
    }

    public Instant getOnboardedAt() {
        return onboardedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
