package com.scentstation.notification.repository;

import com.scentstation.notification.entity.DevicePlatform;
import com.scentstation.notification.entity.FirebaseDeviceRegistration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FirebaseDeviceRegistrationRepositoryTest {
    @Autowired FirebaseDeviceRegistrationRepository registrations;

    @BeforeEach
    void clean() {
        registrations.deleteAll();
    }

    @Test
    void queriesOnlyActiveTokensForRequestedTenantUsers() {
        registrations.save(registration("tenant-a", "user-1", "phone", "token-1", true));
        registrations.save(registration("tenant-a", "user-2", "tablet", "token-2", false));
        registrations.save(registration("tenant-b", "user-1", "web", "token-3", true));
        registrations.flush();

        var found = registrations.findAllByTenantIdAndUserIdInAndActiveTrue(
            "tenant-a", List.of("user-1", "user-2"));

        assertThat(found).extracting(FirebaseDeviceRegistration::getFirebaseToken)
            .containsExactly("token-1");
    }

    @Test
    void databasePreventsOneRawTokenFromBelongingToMultipleRegistrations() {
        registrations.saveAndFlush(registration(
            "tenant-a", "user-1", "phone", "shared-token", true));

        assertThatThrownBy(() -> registrations.saveAndFlush(registration(
            "tenant-b", "user-2", "tablet", "shared-token", true)))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deactivatesOnlyMatchingActiveTokens() {
        registrations.save(registration("tenant-a", "user-1", "phone", "invalid-token", true));
        registrations.save(registration("tenant-a", "user-2", "tablet", "valid-token", true));
        registrations.flush();

        int changed = registrations.deactivateActiveTokens(
            List.of("invalid-token", "missing-token"), Instant.now());
        registrations.flush();

        assertThat(changed).isEqualTo(1);
        assertThat(registrations.findByFirebaseToken("invalid-token").orElseThrow().isActive()).isFalse();
        assertThat(registrations.findByFirebaseToken("valid-token").orElseThrow().isActive()).isTrue();
    }

    private FirebaseDeviceRegistration registration(
            String tenant, String user, String device, String token, boolean active) {
        FirebaseDeviceRegistration registration = new FirebaseDeviceRegistration(
            tenant, user, device, token, DevicePlatform.ANDROID, Instant.now());
        if (!active) registration.revoke(Instant.now());
        return registration;
    }
}
