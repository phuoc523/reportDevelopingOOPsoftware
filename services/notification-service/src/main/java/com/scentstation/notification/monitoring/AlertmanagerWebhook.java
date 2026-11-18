package com.scentstation.notification.monitoring;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

/** Payload contract of Alertmanager's generic webhook receiver. */
public record AlertmanagerWebhook(
        @NotBlank String version,
        @NotBlank String status,
        String receiver,
        String groupKey,
        @NotNull Map<String, String> groupLabels,
        @NotNull Map<String, String> commonLabels,
        @NotNull Map<String, String> commonAnnotations,
        String externalURL,
        @NotEmpty List<@Valid Alert> alerts
) {
    public record Alert(
            @NotBlank String status,
            @NotNull Map<String, String> labels,
            @NotNull Map<String, String> annotations,
            @NotBlank String startsAt,
            String endsAt,
            String generatorURL,
            @NotBlank String fingerprint
    ) {
    }
}
