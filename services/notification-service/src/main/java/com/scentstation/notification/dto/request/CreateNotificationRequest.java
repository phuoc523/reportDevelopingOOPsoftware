package com.scentstation.notification.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateNotificationRequest(
    @Size(max = 100) String tenantId,
    @NotBlank @Size(max = 200) String title,
    @NotBlank @Size(max = 4000) String content,
    @NotBlank @Size(max = 50) String notificationType,
    @NotBlank @Size(max = 20) String priority,
    @NotBlank @Size(max = 50) String source,
    @Size(max = 100) String sourceEventId,
    @NotEmpty @Size(max = 100) List<@NotBlank @Size(max = 100) String> recipientIds
) { }
