package com.scentstation.notification.dto.response;

import java.time.Instant;

public record NotificationApiResponse(
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
    Instant readAt
) {
    public static NotificationApiResponse from(NotificationResponse response) {
        return from(response, null);
    }

    public static NotificationApiResponse from(NotificationResponse response, String recipientId) {
        Instant readAt = response.recipients().stream()
            .filter(recipient -> recipient.recipientId().equals(recipientId))
            .findFirst()
            .map(NotificationResponse.RecipientState::readAt)
            .orElse(null);
        return new NotificationApiResponse(
            response.id(),
            response.tenantId(),
            response.title(),
            response.content(),
            response.notificationType(),
            response.priority(),
            response.source(),
            response.sourceEventId(),
            response.createdAt(),
            response.updatedAt(),
            readAt
        );
    }
}
