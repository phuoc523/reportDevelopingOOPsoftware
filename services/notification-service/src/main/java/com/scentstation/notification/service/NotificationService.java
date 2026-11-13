package com.scentstation.notification.service;

import com.scentstation.notification.dto.command.CreateNotificationCommand;
import com.scentstation.notification.dto.response.NotificationResponse;
import com.scentstation.notification.entity.Notification;
import com.scentstation.notification.exception.DuplicateNotificationEventException;
import com.scentstation.notification.exception.NotificationNotFoundException;
import com.scentstation.notification.repository.NotificationRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.LinkedHashSet;
import java.util.Set;

@Service
@Validated
public class NotificationService {

    private static final String SOURCE_EVENT_CONSTRAINT = "uk_notifications_source_event";

    private final NotificationRepository notifications;

    public NotificationService(NotificationRepository notifications) {
        this.notifications = notifications;
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
