package com.scentstation.notification.security;

import java.util.List;

public record NotificationAuthorizationRequest(
    String tenantId,
    String permission,
    List<String> recipientIds
) { }
