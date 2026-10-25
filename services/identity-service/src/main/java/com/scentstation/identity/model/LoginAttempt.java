package com.scentstation.identity.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "login_attempts", indexes = @Index(name = "idx_attempt_timestamp", columnList = "attempted_at"))
public class LoginAttempt {
    @Id
    @Column(nullable = false, length = 36)
    private String id = UUID.randomUUID().toString();

    @Column(name = "attempted_username", nullable = false, length = 120)
    private String attemptedUsername;

    @Column(name = "user_id", length = 36)
    private String userId;

    @Column(name = "tenant_id", length = 36)
    private String tenantId;

    @Column(nullable = false, length = 40)
    private String result;

    @Column(name = "attempted_at", nullable = false)
    private Instant attemptedAt;

    protected LoginAttempt() {}

    public LoginAttempt(String attemptedUsername, String userId, String tenantId, String result, Instant attemptedAt) {
        this.attemptedUsername = attemptedUsername;
        this.userId = userId;
        this.tenantId = tenantId;
        this.result = result;
        this.attemptedAt = attemptedAt;
    }

    public String getResult() { return result; }
    public Instant getAttemptedAt() { return attemptedAt; }
}
