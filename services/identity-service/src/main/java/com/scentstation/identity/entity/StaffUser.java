package com.scentstation.identity.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Entity
@Table(name = "staff_users")
public class StaffUser {

    @Id
    @Column(name = "id", nullable = false, length = 36)
    private String id;

    @Column(nullable = false, unique = true, length = 120)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "tenant_id", length = 36)
    private String tenantId;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    @Column(name = "last_failed_login_at")
    private LocalDateTime lastFailedLoginAt;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    @Column(name = "manually_locked", nullable = false)
    private boolean manuallyLocked;

    @OneToMany(mappedBy = "staffUser", cascade = CascadeType.ALL,
            orphanRemoval = true, fetch = FetchType.LAZY)
    private Set<StaffUserRole> roles = new LinkedHashSet<>();

    protected StaffUser() {
    }

    public StaffUser(String username, String passwordHash, String tenantId) {
        this.id = UUID.randomUUID().toString();
        this.username = username;
        this.passwordHash = passwordHash;
        this.tenantId = tenantId;
    }

    public void addRole(StaffRole role) {
        StaffUserRoleId roleId = new StaffUserRoleId(id, role.name());
        roles.add(new StaffUserRole(roleId, this));
    }

    public void updateRoles(Set<StaffRole> requestedRoles) {
        Set<String> requestedNames = requestedRoles.stream()
                .map(StaffRole::name)
                .collect(Collectors.toSet());
        roles.removeIf(role -> !requestedNames.contains(role.getId().getRoleName()));
        Set<String> existingNames = roles.stream()
                .map(role -> role.getId().getRoleName())
                .collect(Collectors.toSet());
        requestedRoles.stream()
                .filter(role -> !existingNames.contains(role.name()))
                .forEach(this::addRole);
    }

    public void updateUsernameAndTenant(String username, String tenantId) {
        this.username = username;
        this.tenantId = tenantId;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setManuallyLocked(boolean manuallyLocked) {
        this.manuallyLocked = manuallyLocked;
        if (!manuallyLocked) {
            failedAttempts = 0;
            lastFailedLoginAt = null;
            lockedUntil = null;
        }
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public void recordFailedLogin(LocalDateTime failedAt, int maxAttempts,
                                  LocalDateTime lockUntil) {
        if (lockedUntil != null && !failedAt.isBefore(lockedUntil)) {
            failedAttempts = 0;
        }
        failedAttempts++;
        lastFailedLoginAt = failedAt;
        lockedUntil = failedAttempts >= maxAttempts ? lockUntil : null;
    }

    public void recordSuccessfulLogin(LocalDateTime loginAt) {
        failedAttempts = 0;
        lastFailedLoginAt = null;
        lockedUntil = null;
        lastLoginAt = loginAt;
    }

    public boolean isTemporarilyLockedAt(LocalDateTime now) {
        return lockedUntil != null && now.isBefore(lockedUntil);
    }

    public boolean isUnavailableAt(LocalDateTime now) {
        return !enabled || manuallyLocked || isTemporarilyLockedAt(now);
    }

    public String getId() { return id; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public String getTenantId() { return tenantId; }
    public boolean isEnabled() { return enabled; }
    public int getFailedAttempts() { return failedAttempts; }
    public LocalDateTime getLastFailedLoginAt() { return lastFailedLoginAt; }
    public LocalDateTime getLastLoginAt() { return lastLoginAt; }
    public LocalDateTime getLockedUntil() { return lockedUntil; }
    public boolean isManuallyLocked() { return manuallyLocked; }
    public Set<StaffUserRole> getRoles() { return roles; }
}
