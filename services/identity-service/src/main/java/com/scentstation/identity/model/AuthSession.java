package com.scentstation.identity.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "auth_sessions", indexes = @Index(name = "idx_session_user", columnList = "user_id"))
public class AuthSession {
    @Id
    @Column(nullable = false, length = 36)
    private String id = UUID.randomUUID().toString();

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private StaffUser user;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected AuthSession() {}

    public AuthSession(String tokenHash, StaffUser user, Instant createdAt, Instant expiresAt) {
        this.tokenHash = tokenHash;
        this.user = user;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public String getId() { return id; }
    public String getTokenHash() { return tokenHash; }
    public StaffUser getUser() { return user; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public void setRevokedAt(Instant revokedAt) { this.revokedAt = revokedAt; }
}
