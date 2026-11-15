package com.scentstation.notification.service;

import com.scentstation.notification.dto.response.DeviceRegistrationResponse;
import com.scentstation.notification.entity.DevicePlatform;
import com.scentstation.notification.entity.FirebaseDeviceRegistration;
import com.scentstation.notification.exception.DeviceRegistrationNotFoundException;
import com.scentstation.notification.exception.DeviceTokenConflictException;
import com.scentstation.notification.repository.FirebaseDeviceRegistrationRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FirebaseDeviceRegistrationService {
    private final FirebaseDeviceRegistrationRepository registrations;

    public FirebaseDeviceRegistrationService(FirebaseDeviceRegistrationRepository registrations) {
        this.registrations = registrations;
    }

    @Transactional
    public DeviceRegistrationResponse register(String tenantId, String userId, String deviceId,
                                               String token, DevicePlatform platform) {
        String cleanTenant = tenantId.trim();
        String cleanUser = userId.trim();
        String cleanDevice = deviceId.trim();
        String cleanToken = token.trim();
        Instant now = Instant.now();
        FirebaseDeviceRegistration current = registrations
            .findByTenantIdAndUserIdAndDeviceId(cleanTenant, cleanUser, cleanDevice)
            .orElse(null);
        FirebaseDeviceRegistration tokenOwner = registrations.findByFirebaseToken(cleanToken).orElse(null);

        if (tokenOwner != null && tokenOwner != current) {
            if (tokenOwner.isActive()) {
                throw new DeviceTokenConflictException();
            }
            tokenOwner.releaseToken(now);
            registrations.saveAndFlush(tokenOwner);
        }

        if (current == null) {
            current = new FirebaseDeviceRegistration(
                cleanTenant, cleanUser, cleanDevice, cleanToken, platform, now);
        } else {
            current.register(cleanToken, platform, now);
        }
        try {
            return DeviceRegistrationResponse.from(registrations.saveAndFlush(current));
        } catch (DataIntegrityViolationException exception) {
            throw new DeviceTokenConflictException();
        }
    }

    @Transactional
    public DeviceRegistrationResponse revoke(String tenantId, String userId, String deviceId) {
        FirebaseDeviceRegistration registration = registrations
            .findByTenantIdAndUserIdAndDeviceId(tenantId.trim(), userId.trim(), deviceId.trim())
            .orElseThrow(DeviceRegistrationNotFoundException::new);
        registration.revoke(Instant.now());
        return DeviceRegistrationResponse.from(registrations.save(registration));
    }

    @Transactional(readOnly = true)
    public List<String> findActiveTokens(String tenantId, Collection<String> userIds) {
        return registrations.findAllByTenantIdAndUserIdInAndActiveTrue(tenantId.trim(), userIds)
            .stream().map(FirebaseDeviceRegistration::getFirebaseToken).toList();
    }
}
