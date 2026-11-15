package com.scentstation.notification.event;

import com.scentstation.notification.security.IdentityAuthorizationClient;
import com.scentstation.notification.security.NotificationAuthorizationUnavailableException;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MachineAlertRecipientResolverTest {
    private final IdentityAuthorizationClient identity = mock(IdentityAuthorizationClient.class);
    private final MachineAlertRecipientResolver resolver = new MachineAlertRecipientResolver(identity);

    @Test
    void trimsAndDeduplicatesRecipientsInIdentityOrder() {
        when(identity.resolveNotificationRecipients("7")).thenReturn(List.of(" 42 ", "9", "42"));

        assertEquals(List.of("42", "9"), resolver.resolve("7"));
    }

    @Test
    void failsClosedForMissingOrInvalidRecipients() {
        when(identity.resolveNotificationRecipients("7")).thenReturn(List.of());
        assertThrows(IllegalStateException.class, () -> resolver.resolve("7"));

        when(identity.resolveNotificationRecipients("7")).thenReturn(List.of("invalid"));
        assertThrows(NotificationAuthorizationUnavailableException.class, () -> resolver.resolve("7"));
    }
}
