package com.aicoach.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.springframework.data.domain.Persistable;

/**
 * One link in a refresh-token rotation chain ("family"). Only a SHA-256 hash of the opaque token is stored,
 * so a database leak does not expose usable tokens.
 */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private UUID familyId;

    @Column(nullable = false)
    private String tokenHash;

    @Column(nullable = false)
    private Instant expiresAt;

    private Instant revokedAt;

    private UUID replacedById;

    private String userAgent;

    private String ip;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    /** Ids are assigned in the constructor; tell Spring Data this is an insert, not a merge. */
    @Transient
    private boolean isNew = true;

    protected RefreshToken() {
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

    public RefreshToken(UUID userId, UUID familyId, String tokenHash, Instant expiresAt, String userAgent, String ip) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.familyId = familyId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.userAgent = userAgent;
        this.ip = ip;
    }

    public void revoke(Instant now, UUID replacedBy) {
        if (this.revokedAt == null) {
            this.revokedAt = now;
            this.replacedById = replacedBy;
        }
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    @Override
    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getFamilyId() {
        return familyId;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public UUID getReplacedById() {
        return replacedById;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
