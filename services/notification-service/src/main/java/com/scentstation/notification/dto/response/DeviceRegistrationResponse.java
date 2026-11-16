package com.scentstation.notification.dto.response;

import com.scentstation.notification.entity.DevicePlatform;
import com.scentstation.notification.entity.FirebaseDeviceRegistration;
import java.time.Instant;

public record DeviceRegistrationResponse(
    Long id,
    String tenantId,
    String userId,
    String deviceId,
    DevicePlatform platform,
    boolean active,
    Instant createdAt,
    Instant updatedAt,
    Instant lastSeenAt
) {
    public static DeviceRegistrationResponse from(FirebaseDeviceRegistration registration) {
        return new DeviceRegistrationResponse(registration.getId(), registration.getTenantId(),
            registration.getUserId(), registration.getDeviceId(), registration.getPlatform(),
            registration.isActive(), registration.getCreatedAt(), registration.getUpdatedAt(),
            registration.getLastSeenAt());
    }
}
