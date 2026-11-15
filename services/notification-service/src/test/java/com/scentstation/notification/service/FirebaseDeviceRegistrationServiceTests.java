package com.scentstation.notification.service;

import com.scentstation.notification.entity.DevicePlatform;
import com.scentstation.notification.entity.FirebaseDeviceRegistration;
import com.scentstation.notification.exception.DeviceRegistrationNotFoundException;
import com.scentstation.notification.exception.DeviceTokenConflictException;
import com.scentstation.notification.repository.FirebaseDeviceRegistrationRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class FirebaseDeviceRegistrationServiceTests {
    private FirebaseDeviceRegistrationRepository repository;
    private FirebaseDeviceRegistrationService service;

    @BeforeEach
    void setUp() {
        repository = mock(FirebaseDeviceRegistrationRepository.class);
        service = new FirebaseDeviceRegistrationService(repository);
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void registersNewDeviceWithoutExposingToken() {
        when(repository.findByTenantIdAndUserIdAndDeviceId("7", "42", "phone")).thenReturn(Optional.empty());
        when(repository.findByFirebaseToken("secret-token")).thenReturn(Optional.empty());

        var response = service.register("7", "42", "phone", "secret-token", DevicePlatform.ANDROID);

        assertThat(response.tenantId()).isEqualTo("7");
        assertThat(response.userId()).isEqualTo("42");
        assertThat(response.active()).isTrue();
        ArgumentCaptor<FirebaseDeviceRegistration> saved = ArgumentCaptor.forClass(FirebaseDeviceRegistration.class);
        verify(repository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getFirebaseToken()).isEqualTo("secret-token");
    }

    @Test
    void reRegisteringSameDeviceIsIdempotentAndRotatesToken() {
        FirebaseDeviceRegistration existing = registration("old-token", true);
        when(repository.findByTenantIdAndUserIdAndDeviceId("7", "42", "phone"))
            .thenReturn(Optional.of(existing));
        when(repository.findByFirebaseToken("new-token")).thenReturn(Optional.empty());

        service.register("7", "42", "phone", "new-token", DevicePlatform.IOS);

        assertThat(existing.getFirebaseToken()).isEqualTo("new-token");
        assertThat(existing.getPlatform()).isEqualTo(DevicePlatform.IOS);
        assertThat(existing.isActive()).isTrue();
        verify(repository).saveAndFlush(existing);
    }

    @Test
    void rejectsTokenOwnedByAnotherActiveDevice() {
        FirebaseDeviceRegistration owner = registration("shared-token", true);
        when(repository.findByTenantIdAndUserIdAndDeviceId("8", "99", "tablet"))
            .thenReturn(Optional.empty());
        when(repository.findByFirebaseToken("shared-token")).thenReturn(Optional.of(owner));

        assertThatThrownBy(() -> service.register(
            "8", "99", "tablet", "shared-token", DevicePlatform.ANDROID))
            .isInstanceOf(DeviceTokenConflictException.class);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void transfersTokenAfterPreviousRegistrationWasRevoked() {
        FirebaseDeviceRegistration oldOwner = registration("shared-token", false);
        when(repository.findByTenantIdAndUserIdAndDeviceId("8", "99", "tablet"))
            .thenReturn(Optional.empty());
        when(repository.findByFirebaseToken("shared-token")).thenReturn(Optional.of(oldOwner));

        service.register("8", "99", "tablet", "shared-token", DevicePlatform.WEB);

        assertThat(oldOwner.getFirebaseToken()).isNull();
        verify(repository).saveAndFlush(oldOwner);
        verify(repository, times(2)).saveAndFlush(any());
    }

    @Test
    void revokesOnlyTheOwnedDeviceAndRejectsMissingDevice() {
        FirebaseDeviceRegistration existing = registration("token", true);
        when(repository.findByTenantIdAndUserIdAndDeviceId("7", "42", "phone"))
            .thenReturn(Optional.of(existing));
        assertThat(service.revoke("7", "42", "phone").active()).isFalse();

        when(repository.findByTenantIdAndUserIdAndDeviceId("7", "42", "missing"))
            .thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.revoke("7", "42", "missing"))
            .isInstanceOf(DeviceRegistrationNotFoundException.class);
    }

    @Test
    void resolvesOnlyActiveTokensForTenantUsers() {
        when(repository.findAllByTenantIdAndUserIdInAndActiveTrue("7", List.of("42")))
            .thenReturn(List.of(registration("token", true)));
        assertThat(service.findActiveTokens("7", List.of("42"))).containsExactly("token");
    }

    private FirebaseDeviceRegistration registration(String token, boolean active) {
        FirebaseDeviceRegistration registration = new FirebaseDeviceRegistration(
            "7", "42", "phone", token, DevicePlatform.ANDROID, Instant.now());
        if (!active) registration.revoke(Instant.now());
        return registration;
    }
}
