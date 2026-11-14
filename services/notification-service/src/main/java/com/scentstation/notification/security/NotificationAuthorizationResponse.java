package com.scentstation.notification.security;

import java.util.List;

public record NotificationAuthorizationResponse(
    String callerType,
    Long userId,
    String tenantId,
    List<String> validatedRecipientIds
) {
    public NotificationAuthorizationResponse {
        validatedRecipientIds = List.copyOf(validatedRecipientIds);
    }

    public boolean isUser() {
        return "USER".equals(callerType);
    }
}
