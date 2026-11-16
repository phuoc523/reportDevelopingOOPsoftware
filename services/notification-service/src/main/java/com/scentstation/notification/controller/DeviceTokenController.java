package com.scentstation.notification.controller;

import com.scentstation.notification.dto.request.RegisterDeviceTokenRequest;
import com.scentstation.notification.dto.response.DeviceRegistrationResponse;
import com.scentstation.notification.security.IdentityAuthorizationClient;
import com.scentstation.notification.security.NotificationAuthorizationException;
import com.scentstation.notification.security.NotificationAuthorizationResponse;
import com.scentstation.notification.service.FirebaseDeviceRegistrationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notification-device-tokens")
@Validated
public class DeviceTokenController {
    private static final String READ_PERMISSION = "NOTIFICATION_READ";
    private final FirebaseDeviceRegistrationService registrations;
    private final IdentityAuthorizationClient identity;

    public DeviceTokenController(FirebaseDeviceRegistrationService registrations,
                                 IdentityAuthorizationClient identity) {
        this.registrations = registrations;
        this.identity = identity;
    }

    @PostMapping
    public ResponseEntity<DeviceRegistrationResponse> register(
        @Valid @RequestBody RegisterDeviceTokenRequest request,
        @RequestHeader(value = "Authorization", required = false) String authorization) {
        NotificationAuthorizationResponse caller = authorizeUser(authorization, request.tenantId());
        DeviceRegistrationResponse response = registrations.register(caller.tenantId(),
            caller.userId().toString(), request.deviceId(), request.firebaseToken(), request.platform());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/{deviceId}")
    public DeviceRegistrationResponse revoke(
        @PathVariable @Size(max = 200) String deviceId,
        @RequestParam(required = false) @Size(max = 100) String tenantId,
        @RequestHeader(value = "Authorization", required = false) String authorization) {
        NotificationAuthorizationResponse caller = authorizeUser(authorization, tenantId);
        return registrations.revoke(caller.tenantId(), caller.userId().toString(), deviceId);
    }

    @GetMapping("/{deviceId}")
    public DeviceRegistrationResponse get(
        @PathVariable @Size(max = 200) String deviceId,
        @RequestParam(required = false) @Size(max = 100) String tenantId,
        @RequestHeader(value = "Authorization", required = false) String authorization) {
        NotificationAuthorizationResponse caller = authorizeUser(authorization, tenantId);
        return registrations.get(caller.tenantId(), caller.userId().toString(), deviceId);
    }

    private NotificationAuthorizationResponse authorizeUser(String authorization, String tenantId) {
        NotificationAuthorizationResponse caller = identity.authorize(
            authorization, null, null, READ_PERMISSION, tenantId, List.of());
        if (!caller.isUser()) {
            throw new NotificationAuthorizationException(HttpStatus.FORBIDDEN);
        }
        return caller;
    }
}
