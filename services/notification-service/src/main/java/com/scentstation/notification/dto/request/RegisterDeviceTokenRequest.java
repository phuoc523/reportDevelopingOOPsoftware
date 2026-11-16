package com.scentstation.notification.dto.request;

import com.scentstation.notification.entity.DevicePlatform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterDeviceTokenRequest(
    @Size(max = 100) String tenantId,
    @NotBlank @Size(max = 200) String deviceId,
    @NotBlank @Size(max = 512) String firebaseToken,
    @NotNull DevicePlatform platform
) {}
