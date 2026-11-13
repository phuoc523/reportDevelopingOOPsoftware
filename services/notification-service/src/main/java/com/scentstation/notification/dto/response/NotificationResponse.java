package com.scentstation.notification.dto.response;

import com.scentstation.notification.entity.Notification;

import java.time.Instant;
import java.util.List;

public record NotificationResponse(
    Long id,
    String tenantId,
    String title,
    String content,
    String notificationType,
    String priority,
    String source,
    String sourceEventId,
    Instant createdAt,
    Instant updatedAt,
    List<RecipientState> recipients
) {

    public NotificationResponse {
        recipients = List.copyOf(recipients);
    }

    public static NotificationResponse from(Notification notification) {
        List<RecipientState> recipients = notification.getRecipients().stream()
            .map(recipient -> new RecipientState(
                recipient.getId(),
                recipient.getRecipientId(),
                recipient.getReadAt(),
                recipient.getCreatedAt()
            ))
            .toList();

        return new NotificationResponse(
            notification.getId(),
            notification.getTenantId(),
            notification.getTitle(),
            notification.getContent(),
            notification.getNotificationType(),
            notification.getPriority(),
            notification.getSource(),
            notification.getSourceEventId(),
            notification.getCreatedAt(),
            notification.getUpdatedAt(),
            recipients
        );
    }

    public record RecipientState(
        Long id,
        String recipientId,
        Instant readAt,
        Instant createdAt
    ) {
    }
}
