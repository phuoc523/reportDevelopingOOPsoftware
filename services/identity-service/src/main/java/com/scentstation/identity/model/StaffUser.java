package com.scentstation.identity.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Minimal staff account model. Account CRUD/role assignment belong to P1-02/P1-04. */
@Entity
@Table(name = "staff_users", indexes = @Index(name = "idx_staff_tenant", columnList = "tenant_id"))
public class StaffUser {
    @Id
    @Column(nullable = false, length = 36)
    private String id = UUID.randomUUID().toString();

    @Column(nullable = false, unique = true, length = 120)
    private String username;

    // Optional for legacy staff users; mandatory for new self-registered customers.
    @Column(unique = true, length = 254)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "tenant_id")
    private Tenant tenant;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "staff_user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "role_name", nullable = false, length = 64)
    private Set<String> roles = new HashSet<>();

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "manually_locked", nullable = false)
    private boolean manuallyLocked;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "last_failed_login_at")
    private Instant lastFailedLoginAt;

    protected StaffUser() {}

    public StaffUser(String username, String passwordHash, Tenant tenant, Set<String> roles) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.tenant = tenant;
        this.roles = new HashSet<>(roles);
    }

    public String getId() { return id; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPasswordHash() { return passwordHash; }
    public Tenant getTenant() { return tenant; }
    public Set<String> getRoles() { return roles; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isManuallyLocked() { return manuallyLocked; }
    public void setManuallyLocked(boolean manuallyLocked) { this.manuallyLocked = manuallyLocked; }
    public int getFailedAttempts() { return failedAttempts; }
    public void setFailedAttempts(int failedAttempts) { this.failedAttempts = failedAttempts; }
    public Instant getLockedUntil() { return lockedUntil; }
    public void setLockedUntil(Instant lockedUntil) { this.lockedUntil = lockedUntil; }
    public Instant getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(Instant lastLoginAt) { this.lastLoginAt = lastLoginAt; }
    public Instant getLastFailedLoginAt() { return lastFailedLoginAt; }
    public void setLastFailedLoginAt(Instant lastFailedLoginAt) { this.lastFailedLoginAt = lastFailedLoginAt; }
}
