package com.scentstation.notification.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "firebase_device_registrations", uniqueConstraints = {
    @UniqueConstraint(name = "uk_firebase_device_owner", columnNames = {"tenant_id", "user_id", "device_id"}),
    @UniqueConstraint(name = "uk_firebase_token", columnNames = "firebase_token")
}, indexes = @Index(name = "idx_firebase_active_user", columnList = "tenant_id, user_id, active"))
public class FirebaseDeviceRegistration {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, length = 100)
    private String tenantId;

    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;

    @Column(name = "device_id", nullable = false, length = 200)
    private String deviceId;

    @Column(name = "firebase_token", length = 512)
    private String firebaseToken;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DevicePlatform platform;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    protected FirebaseDeviceRegistration() {}

    public FirebaseDeviceRegistration(String tenantId, String userId, String deviceId,
                                      String firebaseToken, DevicePlatform platform, Instant now) {
        this.tenantId = tenantId;
        this.userId = userId;
        this.deviceId = deviceId;
        this.createdAt = now;
        register(firebaseToken, platform, now);
    }

    public void register(String token, DevicePlatform newPlatform, Instant now) {
        firebaseToken = token;
        platform = newPlatform;
        active = true;
        lastSeenAt = now;
        updatedAt = now;
    }

    public void revoke(Instant now) {
        active = false;
        updatedAt = now;
    }

    public void releaseToken(Instant now) {
        firebaseToken = null;
        updatedAt = now;
    }

    public Long getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getUserId() { return userId; }
    public String getDeviceId() { return deviceId; }
    public String getFirebaseToken() { return firebaseToken; }
    public DevicePlatform getPlatform() { return platform; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getLastSeenAt() { return lastSeenAt; }
}
