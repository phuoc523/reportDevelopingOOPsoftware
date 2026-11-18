package com.scentstation.notification.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.scentstation.notification.monitoring.AlertmanagerNotificationProcessor;
import com.scentstation.notification.monitoring.AlertmanagerWebhook;
import com.scentstation.notification.monitoring.AlertmanagerWebhookResult;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class InternalAlertmanagerControllerTest {
    private final AlertmanagerNotificationProcessor processor = mock(AlertmanagerNotificationProcessor.class);
    private final InternalAlertmanagerController controller =
        new InternalAlertmanagerController(processor, "internal-secret");
    private final AlertmanagerWebhook payload = new AlertmanagerWebhook(
        "4", "firing", "receiver", "group", Map.of(), Map.of(), Map.of(), "http://alertmanager",
        List.of(new AlertmanagerWebhook.Alert(
            "firing", Map.of("alertname", "BackendDown"), Map.of(),
            "2026-11-18T12:00:00Z", "0001-01-01T00:00:00Z", "http://prometheus", "abc")));

    @Test
    void acceptsMatchingBearerCredential() {
        var expected = new AlertmanagerWebhookResult(1, 1, 0, 0);
        when(processor.process(payload)).thenReturn(expected);

        assertThat(controller.accept("Bearer internal-secret", payload)).isEqualTo(expected);
        verify(processor).process(payload);
    }

    @Test
    void rejectsMissingOrWrongCredentialBeforeProcessing() {
        assertThatThrownBy(() -> controller.accept("Bearer wrong", payload))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> controller.accept(null, payload))
            .isInstanceOf(ResponseStatusException.class);
    }
}
