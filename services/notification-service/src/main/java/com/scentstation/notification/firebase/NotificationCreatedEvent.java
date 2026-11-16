package com.scentstation.notification.firebase;

import java.util.List;

public record NotificationCreatedEvent(
    Long notificationId,
    String tenantId,
    String title,
    String content,
    String notificationType,
    String priority,
    String source,
    String sourceEventId,
    List<String> recipientIds
) {
    public NotificationCreatedEvent {
        recipientIds = List.copyOf(recipientIds);
    }
}
