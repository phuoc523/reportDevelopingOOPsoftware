package com.scentstation.notification.monitoring;

import com.scentstation.notification.dto.command.CreateNotificationCommand;
import com.scentstation.notification.event.MachineAlertRecipientResolver;
import com.scentstation.notification.exception.DuplicateNotificationEventException;
import com.scentstation.notification.service.NotificationService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AlertmanagerNotificationProcessor {
    private static final Logger log = LoggerFactory.getLogger(AlertmanagerNotificationProcessor.class);
    private static final String SOURCE = "PROMETHEUS";

    private final NotificationService notifications;
    private final MachineAlertRecipientResolver recipients;
    private final String defaultTenantId;

    public AlertmanagerNotificationProcessor(
            NotificationService notifications,
            MachineAlertRecipientResolver recipients,
            @Value("${notification.monitoring.default-tenant-id:}") String defaultTenantId
    ) {
        this.notifications = notifications;
        this.recipients = recipients;
        this.defaultTenantId = normalizeTenant(defaultTenantId);
    }

    public AlertmanagerWebhookResult process(AlertmanagerWebhook webhook) {
        int created = 0;
        int duplicates = 0;
        int skipped = 0;
        Map<String, List<String>> recipientsByTenant = new HashMap<>();

        for (AlertmanagerWebhook.Alert alert : webhook.alerts()) {
            String tenantId = resolveTenant(alert.labels(), webhook.commonLabels());
            if (tenantId == null) {
                skipped++;
                log.warn("Skipping Alertmanager alert {} because no valid tenant_id/default tenant is configured",
                    alert.fingerprint());
                continue;
            }

            String sourceEventId = sourceEventId(alert);
            if (notifications.existsForSourceEvent(tenantId, SOURCE, sourceEventId)) {
                duplicates++;
                continue;
            }

            try {
                List<String> recipientIds = recipientsByTenant.computeIfAbsent(tenantId, recipients::resolve);
                notifications.create(toCommand(tenantId, alert, recipientIds));
                created++;
            } catch (DuplicateNotificationEventException exception) {
                duplicates++;
            }
        }
        return new AlertmanagerWebhookResult(webhook.alerts().size(), created, duplicates, skipped);
    }

    private CreateNotificationCommand toCommand(
            String tenantId,
            AlertmanagerWebhook.Alert alert,
            List<String> recipientIds
    ) {
        String alertName = value(alert.labels(), "alertname", "Cảnh báo hệ thống");
        String service = value(alert.labels(), "service", value(alert.labels(), "instance", "backend"));
        String severity = value(alert.labels(), "severity", "warning").toLowerCase();
        boolean resolved = "resolved".equalsIgnoreCase(alert.status());
        String title = resolved
            ? "Đã khôi phục: " + alertName
            : priorityPrefix(severity) + alertName;
        String summary = value(alert.annotations(), "summary", "Không có mô tả ngắn.");
        String description = value(alert.annotations(), "description", "Không có mô tả chi tiết.");
        String content = "Dịch vụ: %s. Trạng thái: %s. %s %s Bắt đầu: %s%s"
            .formatted(service, resolved ? "Đã khôi phục" : "Đang xảy ra", summary, description,
                alert.startsAt(), resolved && alert.endsAt() != null ? ". Kết thúc: " + alert.endsAt() : ".");

        return new CreateNotificationCommand(
            tenantId,
            truncate(title, 200),
            truncate(content, 4000),
            "SYSTEM_ALERT",
            priority(severity, resolved),
            SOURCE,
            sourceEventId(alert),
            recipientIds
        );
    }

    private String resolveTenant(Map<String, String> labels, Map<String, String> commonLabels) {
        String tenant = normalizeTenant(labels.get("tenant_id"));
        if (tenant == null) tenant = normalizeTenant(commonLabels.get("tenant_id"));
        return tenant == null ? defaultTenantId : tenant;
    }

    private String normalizeTenant(String value) {
        if (value == null || !value.trim().matches("[1-9][0-9]*")) return null;
        return value.trim();
    }

    private String sourceEventId(AlertmanagerWebhook.Alert alert) {
        String raw = alert.fingerprint() + "|" + alert.status().toLowerCase() + "|" + alert.startsAt();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String priority(String severity, boolean resolved) {
        if (resolved) return "LOW";
        return switch (severity) {
            case "critical" -> "CRITICAL";
            case "warning" -> "HIGH";
            default -> "MEDIUM";
        };
    }

    private String priorityPrefix(String severity) {
        return switch (severity) {
            case "critical" -> "Nghiêm trọng: ";
            case "warning" -> "Cảnh báo: ";
            default -> "Hệ thống: ";
        };
    }

    private String value(Map<String, String> values, String key, String fallback) {
        String value = values.get(key);
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength - 1) + "…";
    }
}
