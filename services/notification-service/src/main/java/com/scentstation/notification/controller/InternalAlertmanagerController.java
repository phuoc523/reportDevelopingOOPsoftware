package com.scentstation.notification.controller;

import com.scentstation.notification.monitoring.AlertmanagerNotificationProcessor;
import com.scentstation.notification.monitoring.AlertmanagerWebhook;
import com.scentstation.notification.monitoring.AlertmanagerWebhookResult;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/internal/events/prometheus-alerts")
public class InternalAlertmanagerController {
    private static final String BEARER_PREFIX = "Bearer ";

    private final AlertmanagerNotificationProcessor processor;
    private final String serviceKey;

    public InternalAlertmanagerController(
            AlertmanagerNotificationProcessor processor,
            @Value("${notification.identity.internal-service-key:}") String serviceKey
    ) {
        this.processor = processor;
        this.serviceKey = serviceKey;
    }

    @PostMapping
    public AlertmanagerWebhookResult accept(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody AlertmanagerWebhook webhook
    ) {
        requireInternalKey(authorization);
        return processor.process(webhook);
    }

    private void requireInternalKey(String authorization) {
        String suppliedKey = authorization != null && authorization.startsWith(BEARER_PREFIX)
            ? authorization.substring(BEARER_PREFIX.length()) : null;
        if (serviceKey == null || serviceKey.isBlank() || suppliedKey == null
                || !MessageDigest.isEqual(
                    serviceKey.getBytes(StandardCharsets.UTF_8),
                    suppliedKey.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Service authentication required");
        }
    }
}
