package com.scentstation.notification.firebase;

import com.scentstation.notification.service.FirebaseDeviceRegistrationService;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FirebaseNotificationDeliveryServiceTest {
    private final FirebaseDeviceRegistrationService devices = mock(FirebaseDeviceRegistrationService.class);
    private final FirebaseCloudMessagingProvider provider = mock(FirebaseCloudMessagingProvider.class);
    private final FirebaseNotificationDeliveryService delivery =
        new FirebaseNotificationDeliveryService(devices, provider);

    @Test
    void sendsToRecipientTokensAndDeactivatesInvalidOnes() {
        when(devices.findActiveTokens("tenant-a", List.of("user-1")))
            .thenReturn(List.of("valid-token", "invalid-token"));
        when(provider.send(any())).thenReturn(new FcmDeliveryResult(
            2, 1, 1, List.of("invalid-token")));

        delivery.deliver(event());

        verify(provider).send(argThat(message ->
            message.tokens().equals(List.of("valid-token", "invalid-token"))
                && message.title().equals("Machine offline")
                && message.data().get("notificationId").equals("12")
                && message.data().get("sourceEventId").equals("event-1")));
        verify(devices).deactivateTokens(List.of("invalid-token"));
    }

    @Test
    void skipsFirebaseWhenRecipientsHaveNoActiveTokens() {
        when(devices.findActiveTokens("tenant-a", List.of("user-1"))).thenReturn(List.of());
        delivery.deliver(event());
        verifyNoInteractions(provider);
    }

    @Test
    void containsProviderFailureSoInboxCreationRemainsSuccessful() {
        when(devices.findActiveTokens(anyString(), anyList())).thenReturn(List.of("token"));
        when(provider.send(any())).thenThrow(new FcmDeliveryException(new RuntimeException("offline")));

        assertThatCode(() -> delivery.deliver(event())).doesNotThrowAnyException();
        verify(devices, never()).deactivateTokens(any());
    }

    private NotificationCreatedEvent event() {
        return new NotificationCreatedEvent(12L, "tenant-a", "Machine offline", "Check machine",
            "MACHINE_ALERT", "CRITICAL", "machine-service", "event-1", List.of("user-1"));
    }
}
