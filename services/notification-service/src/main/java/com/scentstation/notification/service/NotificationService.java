package com.scentstation.notification.service;

import com.scentstation.notification.dto.command.CreateNotificationCommand;
import com.scentstation.notification.dto.response.NotificationResponse;
import com.scentstation.notification.entity.Notification;
import com.scentstation.notification.exception.DuplicateNotificationEventException;
import com.scentstation.notification.exception.NotificationNotFoundException;
import com.scentstation.notification.repository.NotificationRepository;
import com.scentstation.notification.repository.NotificationRecipientRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@Validated
public class NotificationService {

    private static final String SOURCE_EVENT_CONSTRAINT = "uk_notifications_source_event";

    private final NotificationRepository notifications;
    private final NotificationRecipientRepository recipients;

    public NotificationService(
            NotificationRepository notifications,
            NotificationRecipientRepository recipients
    ) {
        this.notifications = notifications;
        this.recipients = recipients;
    }

    @Transactional
    public NotificationResponse create(@Valid @NotNull CreateNotificationCommand command) {
        String tenantId = command.tenantId().trim();
        String source = command.source().trim();
        String sourceEventId = normalizeOptional(command.sourceEventId());

        if (sourceEventId != null && notifications.existsBySourceAndSourceEventId(source, sourceEventId)) {
            throw new DuplicateNotificationEventException();
        }

        Notification notification = new Notification(
            tenantId,
            command.title().trim(),
            command.content().trim(),
            command.notificationType().trim(),
            command.priority().trim(),
            source,
            sourceEventId
        );

        Set<String> recipientIds = new LinkedHashSet<>();
        command.recipientIds().stream()
            .map(String::trim)
            .forEach(recipientIds::add);
        recipientIds.forEach(notification::addRecipient);

        try {
            return NotificationResponse.from(notifications.saveAndFlush(notification));
        } catch (DataIntegrityViolationException exception) {
            if (isSourceEventDuplicate(exception)) {
                throw new DuplicateNotificationEventException();
            }
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public NotificationResponse getById(
            @NotNull Long notificationId,
            @NotBlank String tenantId
    ) {
        Notification notification = notifications.findByIdAndTenantId(notificationId, tenantId.trim())
            .orElseThrow(NotificationNotFoundException::new);
        return NotificationResponse.from(notification);
    }

    @Transactional(readOnly = true)
    public Page<NotificationResponse> listByTenant(
            @NotBlank String tenantId,
            @NotNull Pageable pageable
    ) {
        return notifications.findAllByTenantIdOrderByCreatedAtDescIdDesc(tenantId.trim(), pageable)
            .map(NotificationResponse::from);
    }

    @Transactional(readOnly = true)
    public NotificationResponse getForRecipient(
            @NotNull Long notificationId,
            @NotBlank String tenantId,
            @NotBlank String recipientId
    ) {
        var recipient = recipients.findByNotification_IdAndNotification_TenantIdAndRecipientId(
            notificationId,
            tenantId.trim(),
            recipientId.trim()
        ).orElseThrow(NotificationNotFoundException::new);
        return NotificationResponse.from(recipient.getNotification());
    }

    @Transactional
    public NotificationResponse markAsReadForRecipient(
            @NotNull Long notificationId,
            @NotBlank String tenantId,
            @NotBlank String recipientId
    ) {
        var recipient = recipients.findByNotification_IdAndNotification_TenantIdAndRecipientId(
            notificationId,
            tenantId.trim(),
            recipientId.trim()
        ).orElseThrow(NotificationNotFoundException::new);
        recipient.markAsRead();
        return NotificationResponse.from(recipient.getNotification());
    }

    @Transactional
    public int markManyAsReadForRecipient(
            @NotBlank String tenantId,
            @NotBlank String recipientId,
            @NotEmpty List<@NotNull @Positive Long> notificationIds
    ) {
        Set<Long> uniqueIds = new LinkedHashSet<>(notificationIds);
        return recipients.markUnreadNotificationsAsRead(
            tenantId.trim(),
            recipientId.trim(),
            List.copyOf(uniqueIds),
            Instant.now()
        );
    }

    @Transactional(readOnly = true)
    public long countUnreadForRecipient(@NotBlank String tenantId, @NotBlank String recipientId) {
        return recipients.countByNotification_TenantIdAndRecipientIdAndReadAtIsNull(
            tenantId.trim(),
            recipientId.trim()
        );
    }

    @Transactional(readOnly = true)
    public Page<NotificationResponse> listByTenant(
            @NotBlank String tenantId,
            String notificationType,
            String priority,
            @NotNull Pageable pageable
    ) {
        return notifications.findPageByTenant(
            tenantId.trim(),
            normalizeOptional(notificationType),
            normalizeOptional(priority),
            withDefaultSort(pageable, "createdAt", "id")
        ).map(NotificationResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<NotificationResponse> listForRecipient(
            @NotBlank String tenantId,
            @NotBlank String recipientId,
            String notificationType,
            String priority,
            @NotNull Pageable pageable
    ) {
        return recipients.findInboxPage(
            tenantId.trim(),
            recipientId.trim(),
            normalizeOptional(notificationType),
            normalizeOptional(priority),
            withDefaultSort(pageable, "notification.createdAt", "notification.id")
        ).map(item -> NotificationResponse.from(item.getNotification()));
    }

    private Pageable withDefaultSort(Pageable pageable, String firstProperty, String secondProperty) {
        if (pageable.getSort().isSorted()) {
            return pageable;
        }
        return PageRequest.of(
            pageable.getPageNumber(),
            pageable.getPageSize(),
            Sort.by(Sort.Direction.DESC, firstProperty).and(Sort.by(Sort.Direction.DESC, secondProperty))
        );
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private boolean isSourceEventDuplicate(DataIntegrityViolationException exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation
                    && SOURCE_EVENT_CONSTRAINT.equals(violation.getConstraintName())) {
                return true;
            }
        }
        return false;
    }
}
