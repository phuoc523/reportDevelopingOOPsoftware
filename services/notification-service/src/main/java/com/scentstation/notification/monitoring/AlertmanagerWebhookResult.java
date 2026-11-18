package com.scentstation.notification.monitoring;

public record AlertmanagerWebhookResult(int received, int created, int duplicates, int skipped) {
}
