package com.scentstation.notification.monitoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.scentstation.notification.dto.command.CreateNotificationCommand;
import com.scentstation.notification.dto.response.NotificationResponse;
import com.scentstation.notification.event.MachineAlertRecipientResolver;
import com.scentstation.notification.service.NotificationService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class AlertmanagerNotificationProcessorTest {
    private final NotificationService notifications = Mockito.mock(NotificationService.class);
    private final MachineAlertRecipientResolver recipients = Mockito.mock(MachineAlertRecipientResolver.class);
    private final AlertmanagerNotificationProcessor processor =
        new AlertmanagerNotificationProcessor(notifications, recipients, "7");

    @Test
    void createsVietnameseTenantScopedNotification() {
        when(notifications.existsForSourceEvent(anyString(), anyString(), anyString())).thenReturn(false);
        when(recipients.resolve("12")).thenReturn(List.of("4", "9"));
        when(notifications.create(any())).thenReturn(Mockito.mock(NotificationResponse.class));

        AlertmanagerWebhookResult result = processor.process(webhook("firing", Map.of(
            "alertname", "BackendDown", "severity", "critical", "service", "order-service", "tenant_id", "12")));

        assertThat(result).isEqualTo(new AlertmanagerWebhookResult(1, 1, 0, 0));
        ArgumentCaptor<CreateNotificationCommand> command = ArgumentCaptor.forClass(CreateNotificationCommand.class);
        verify(notifications).create(command.capture());
        assertThat(command.getValue().tenantId()).isEqualTo("12");
        assertThat(command.getValue().title()).isEqualTo("Nghiêm trọng: BackendDown");
        assertThat(command.getValue().notificationType()).isEqualTo("SYSTEM_ALERT");
        assertThat(command.getValue().priority()).isEqualTo("CRITICAL");
        assertThat(command.getValue().source()).isEqualTo("PROMETHEUS");
        assertThat(command.getValue().recipientIds()).containsExactly("4", "9");
        assertThat(command.getValue().sourceEventId()).hasSize(64);
    }

    @Test
    void usesDefaultTenantAndAcknowledgesRetryWithoutCreatingDuplicate() {
        when(notifications.existsForSourceEvent(anyString(), anyString(), anyString())).thenReturn(true);

        AlertmanagerWebhookResult result = processor.process(webhook("resolved", Map.of(
            "alertname", "HighJvmHeapUsage", "severity", "warning")));

        assertThat(result).isEqualTo(new AlertmanagerWebhookResult(1, 0, 1, 0));
        verify(notifications).existsForSourceEvent(org.mockito.ArgumentMatchers.eq("7"),
            org.mockito.ArgumentMatchers.eq("PROMETHEUS"), anyString());
    }

    private AlertmanagerWebhook webhook(String status, Map<String, String> labels) {
        var alert = new AlertmanagerWebhook.Alert(
            status,
            labels,
            Map.of("summary", "Thông báo kiểm thử", "description", "Mô tả kiểm thử."),
            "2026-11-18T12:00:00Z",
            "resolved".equals(status) ? "2026-11-18T12:05:00Z" : "0001-01-01T00:00:00Z",
            "http://prometheus/graph",
            "0123456789abcdef"
        );
        return new AlertmanagerWebhook(
            "4", status, "scentstation-notification-service", "group-key",
            Map.of(), Map.of(), Map.of(), "http://alertmanager:9093", List.of(alert));
    }
}
