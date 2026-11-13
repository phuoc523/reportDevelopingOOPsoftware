package com.scentstation.notification.service;

import com.scentstation.notification.dto.command.CreateNotificationCommand;
import com.scentstation.notification.exception.DuplicateNotificationEventException;
import com.scentstation.notification.exception.NotificationNotFoundException;
import com.scentstation.notification.repository.NotificationRecipientRepository;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class NotificationServiceTests {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationRecipientRepository recipients;

    @Test
    void createsNotificationWithIndependentUnreadRecipientRows() {
        var response = notificationService.create(command(
            "tenant-a",
            "event-100",
            List.of("user-1", "user-2")
        ));

        assertNotNull(response.id());
        assertEquals(2, response.recipients().size());
        assertEquals(2, recipients.countByNotification_TenantIdAndRecipientIdAndReadAtIsNull(
            "tenant-a",
            "user-1"
        ) + recipients.countByNotification_TenantIdAndRecipientIdAndReadAtIsNull("tenant-a", "user-2"));

        var userInbox = recipients.findAllByNotification_TenantIdAndRecipientIdOrderByCreatedAtDesc(
            "tenant-a",
            "user-1",
            PageRequest.of(0, 10)
        );
        assertEquals(1, userInbox.getTotalElements());
        assertEquals("user-1", userInbox.getContent().get(0).getRecipientId());

        var recipient = userInbox.getContent().get(0);
        recipient.markAsRead();
        recipients.saveAndFlush(recipient);
        assertEquals(0, recipients.countByNotification_TenantIdAndRecipientIdAndReadAtIsNull(
            "tenant-a",
            "user-1"
        ));

        recipient.markAsUnread();
        recipients.saveAndFlush(recipient);
        assertEquals(1, recipients.countByNotification_TenantIdAndRecipientIdAndReadAtIsNull(
            "tenant-a",
            "user-1"
        ));
    }

    @Test
    void rejectsDuplicateSourceEvent() {
        notificationService.create(command("tenant-a", "event-200", List.of("user-1")));

        assertThrows(
            DuplicateNotificationEventException.class,
            () -> notificationService.create(command("tenant-a", "event-200", List.of("user-2")))
        );
    }

    @Test
    void scopesNotificationLookupToTenant() {
        var created = notificationService.create(command("tenant-a", null, List.of("user-1")));

        assertThrows(
            NotificationNotFoundException.class,
            () -> notificationService.getById(created.id(), "tenant-b")
        );
        assertEquals(created.id(), notificationService.getById(created.id(), "tenant-a").id());
    }

    @Test
    void validatesCommandBeforePersistence() {
        var invalid = new CreateNotificationCommand(
            " ",
            "",
            "",
            "",
            "",
            "",
            null,
            List.of()
        );

        assertThrows(ConstraintViolationException.class, () -> notificationService.create(invalid));
    }

    private CreateNotificationCommand command(String tenantId, String eventId, List<String> recipientIds) {
        return new CreateNotificationCommand(
            tenantId,
            "Machine alert",
            "A machine alert requires attention.",
            "ALERT",
            "HIGH",
            "machine-service",
            eventId,
            recipientIds
        );
    }
}
