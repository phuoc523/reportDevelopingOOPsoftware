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
import org.springframework.data.domain.Sort;
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
        assertThrows(
            NotificationNotFoundException.class,
            () -> notificationService.getForRecipient(created.id(), "tenant-a", "user-2")
        );
        assertEquals(
            created.id(),
            notificationService.getForRecipient(created.id(), "tenant-a", "user-1").id()
        );
    }

    @Test
    void listsOnlyTheCurrentRecipientsNotifications() {
        notificationService.create(command("tenant-a", "event-recipient-a", List.of("user-1")));
        notificationService.create(command("tenant-a", "event-recipient-b", List.of("user-2")));

        var page = notificationService.listForRecipient(
            "tenant-a", "user-1", null, null, PageRequest.of(0, 10)
        );

        assertEquals(1, page.getTotalElements());
        assertEquals("tenant-a", page.getContent().get(0).tenantId());
    }

    @Test
    void marksOneRecipientNotificationReadIdempotently() {
        var created = notificationService.create(command("tenant-a", "event-read-one", List.of("user-1", "user-2")));

        var firstRead = notificationService.markAsReadForRecipient(created.id(), "tenant-a", "user-1");
        var readAt = firstRead.recipients().stream()
            .filter(recipient -> recipient.recipientId().equals("user-1"))
            .findFirst()
            .orElseThrow()
            .readAt();
        var secondRead = notificationService.markAsReadForRecipient(created.id(), "tenant-a", "user-1");

        assertNotNull(readAt);
        assertEquals(readAt, secondRead.recipients().stream()
            .filter(recipient -> recipient.recipientId().equals("user-1"))
            .findFirst()
            .orElseThrow()
            .readAt());
        assertEquals(0, notificationService.countUnreadForRecipient("tenant-a", "user-1"));
        assertEquals(1, notificationService.countUnreadForRecipient("tenant-a", "user-2"));
        assertThrows(
            NotificationNotFoundException.class,
            () -> notificationService.markAsReadForRecipient(created.id(), "tenant-a", "user-3")
        );
    }

    @Test
    void bulkReadUpdatesOnlyUnreadNotificationsOwnedByRecipientInTenant() {
        var first = notificationService.create(command(
            "tenant-a", "event-bulk-first", List.of("user-1", "user-2")
        ));
        var second = notificationService.create(command("tenant-a", "event-bulk-second", List.of("user-1")));
        var otherTenant = notificationService.create(command("tenant-b", "event-bulk-other", List.of("user-1")));
        notificationService.markAsReadForRecipient(first.id(), "tenant-a", "user-1");

        int updated = notificationService.markManyAsReadForRecipient(
            "tenant-a", "user-1", List.of(first.id(), second.id(), otherTenant.id(), second.id(), 999999L)
        );

        assertEquals(1, updated);
        assertEquals(0, notificationService.countUnreadForRecipient("tenant-a", "user-1"));
        assertEquals(1, notificationService.countUnreadForRecipient("tenant-a", "user-2"));
        assertEquals(1, notificationService.countUnreadForRecipient("tenant-b", "user-1"));
    }

    @Test
    void sortsRecipientInboxInRequestedDirectionWithStableIdTieBreaker() {
        var older = notificationService.create(command("tenant-a", "event-sort-older", List.of("user-1")));
        var newer = notificationService.create(command("tenant-a", "event-sort-newer", List.of("user-1")));

        var ascending = notificationService.listForRecipient(
            "tenant-a",
            "user-1",
            null,
            null,
            PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "notification.createdAt")
                .and(Sort.by(Sort.Direction.ASC, "notification.id")))
        );
        var descending = notificationService.listForRecipient(
            "tenant-a",
            "user-1",
            null,
            null,
            PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "notification.createdAt")
                .and(Sort.by(Sort.Direction.DESC, "notification.id")))
        );

        assertEquals(older.id(), ascending.getContent().get(0).id());
        assertEquals(newer.id(), descending.getContent().get(0).id());
    }

    @Test
    void appliesTypeAndPriorityFiltersToTenantAndRecipientLists() {
        notificationService.create(command("tenant-a", "event-filter-alert", List.of("user-1")));
        notificationService.create(new CreateNotificationCommand(
            "tenant-a", "Info", "Information", "INFO", "LOW", "machine-service",
            "event-filter-info", List.of("user-1")
        ));

        var tenantPage = notificationService.listByTenant(
            "tenant-a", "INFO", "LOW", PageRequest.of(0, 10)
        );
        var inboxPage = notificationService.listForRecipient(
            "tenant-a", "user-1", "INFO", "LOW", PageRequest.of(0, 10)
        );

        assertEquals(1, tenantPage.getTotalElements());
        assertEquals("INFO", tenantPage.getContent().get(0).notificationType());
        assertEquals(1, inboxPage.getTotalElements());
        assertEquals("INFO", inboxPage.getContent().get(0).notificationType());
    }

    @Test
    void listsNotificationsWithinTenantUsingStablePagination() {
        var firstCreated = notificationService.create(command("tenant-a", "event-300", List.of("user-1")));
        var secondCreated = notificationService.create(command("tenant-a", "event-301", List.of("user-1")));
        notificationService.create(command("tenant-b", "event-302", List.of("user-2")));

        var firstPage = notificationService.listByTenant(" tenant-a ", PageRequest.of(0, 1));
        var secondPage = notificationService.listByTenant("tenant-a", PageRequest.of(1, 1));

        assertEquals(2, firstPage.getTotalElements());
        assertEquals(2, firstPage.getTotalPages());
        assertEquals(1, firstPage.getContent().size());
        assertEquals("tenant-a", firstPage.getContent().get(0).tenantId());
        assertEquals(1, secondPage.getContent().size());
        assertEquals("tenant-a", secondPage.getContent().get(0).tenantId());
        assertEquals(secondCreated.id(), firstPage.getContent().get(0).id());
        assertEquals(firstCreated.id(), secondPage.getContent().get(0).id());
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
