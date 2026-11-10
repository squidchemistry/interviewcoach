package com.aicoach.user.domain;

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
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Always stored lower-cased (the column is also citext, so lookups are case-insensitive twice over). */
    @Column(nullable = false, columnDefinition = "citext")
    private String email;

    private String passwordHash;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role = Role.USER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Plan plan = Plan.FREE;

    private String googleSub;

    @Column(nullable = false)
    private boolean emailVerified;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    private Instant deletedAt;

    @Version
    private long version;

    protected User() {
    }

    public static User withPassword(String email, String name, String passwordHash) {
        User user = new User();
        user.email = email;
        user.name = name;
        user.passwordHash = passwordHash;
        return user;
    }

    public static User withGoogle(String email, String name, String googleSub) {
        User user = new User();
        user.email = email;
        user.name = name;
        user.googleSub = googleSub;
        user.emailVerified = true;
        return user;
    }

    /** Links a Google identity to this account; Google has verified the email address. */
    public void linkGoogle(String sub) {
        this.googleSub = sub;
        this.emailVerified = true;
    }

    public void changeRole(Role newRole) {
        this.role = newRole;
    }

    /** Set only by billing, from a verified payment-provider event. */
    public void changePlan(Plan newPlan) {
        this.plan = newPlan;
    }

    /**
     * Soft delete with anonymisation: personal data is removed, the row stays so foreign keys (audit logs, later
     * interview history aggregates) remain valid, and the original email becomes free for a new registration.
     */
    public void anonymise(Instant now) {
        this.email = "deleted+" + id + "@deleted.invalid";
        this.name = "Deleted user";
        this.passwordHash = null;
        this.googleSub = null;
        this.emailVerified = false;
        this.deletedAt = now;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getName() {
        return name;
    }

    public Role getRole() {
        return role;
    }

    public Plan getPlan() {
        return plan;
    }

    public String getGoogleSub() {
        return googleSub;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }
}
