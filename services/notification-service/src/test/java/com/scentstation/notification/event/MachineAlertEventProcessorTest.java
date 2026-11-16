package com.scentstation.notification.event;

import com.scentstation.notification.dto.command.CreateNotificationCommand;
import com.scentstation.notification.exception.DuplicateNotificationEventException;
import com.scentstation.notification.service.NotificationService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MachineAlertEventProcessorTest {
    private final NotificationService notifications = mock(NotificationService.class);
    private final MachineAlertNotificationMapper mapper = mock(MachineAlertNotificationMapper.class);
    private final MachineAlertRecipientResolver recipients = mock(MachineAlertRecipientResolver.class);
    private final MachineAlertEventProcessor processor =
        new MachineAlertEventProcessor(notifications, mapper, recipients);

    @Test
    void acknowledgesKnownDuplicateWithoutCallingIdentityOrMapper() {
        MachineAlertEvent event = event();
        when(notifications.existsForSourceEvent("7", "machine-service", "event-1")).thenReturn(true);

        assertFalse(processor.process(event));

        verifyNoInteractions(recipients, mapper);
        verify(notifications, never()).create(any());
    }

    @Test
    void resolvesMapsAndPersistsNewEvent() {
        MachineAlertEvent event = event();
        List<String> recipientIds = List.of("42");
        CreateNotificationCommand command = command(recipientIds);
        when(recipients.resolve("7")).thenReturn(recipientIds);
        when(mapper.map(event, recipientIds)).thenReturn(command);

        assertTrue(processor.process(event));

        verify(notifications).create(command);
    }

    @Test
    void acknowledgesDuplicateDetectedByDatabaseRace() {
        MachineAlertEvent event = event();
        List<String> recipientIds = List.of("42");
        CreateNotificationCommand command = command(recipientIds);
        when(recipients.resolve("7")).thenReturn(recipientIds);
        when(mapper.map(event, recipientIds)).thenReturn(command);
        when(notifications.create(command)).thenThrow(new DuplicateNotificationEventException());

        assertFalse(processor.process(event));
    }

    private MachineAlertEvent event() {
        return new MachineAlertEvent(1, "event-1", 11L, "7", 22L, "3", "MACHINE_OFFLINE",
            "CRITICAL", "OPEN", Instant.parse("2026-11-15T00:00:00Z"), "machine-service");
    }

    private CreateNotificationCommand command(List<String> recipientIds) {
        return new CreateNotificationCommand("7", "Critical: Machine offline", "content",
            "MACHINE_ALERT", "CRITICAL", "machine-service", "event-1", recipientIds);
    }
}
