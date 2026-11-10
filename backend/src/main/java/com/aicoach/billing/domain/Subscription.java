package com.aicoach.billing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** A Pro subscription at a payment provider; its status mirrors the provider's verified events. */
@Entity
@Table(name = "subscriptions")
public class Subscription {

    /** Statuses in which the user has Pro. */
    public static final Set<String> ENTITLED = Set.of("ACTIVE", "AUTHENTICATED", "PENDING");
    public static final Set<String> OPEN = Set.of("CREATED", "AUTHENTICATED", "ACTIVE", "PENDING");

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, updatable = false)
    private String provider;

    @Column(nullable = false, updatable = false)
    private String providerSubscriptionId;

    @Column(nullable = false, updatable = false)
    private String planId;

    @Column(nullable = false)
    private String status = "CREATED";

    private Instant currentEnd;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    protected Subscription() {
    }

    public Subscription(UUID userId, String provider, String providerSubscriptionId, String planId) {
        this.userId = userId;
        this.provider = provider;
        this.providerSubscriptionId = providerSubscriptionId;
        this.planId = planId;
    }

    public void update(String status, Instant currentEnd) {
        this.status = status;
        if (currentEnd != null) {
            this.currentEnd = currentEnd;
        }
    }

    public boolean entitled() {
        return ENTITLED.contains(status);
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getProvider() {
        return provider;
    }

    public String getProviderSubscriptionId() {
        return providerSubscriptionId;
    }

    public String getPlanId() {
        return planId;
    }

    public String getStatus() {
        return status;
    }

    public Instant getCurrentEnd() {
        return currentEnd;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
