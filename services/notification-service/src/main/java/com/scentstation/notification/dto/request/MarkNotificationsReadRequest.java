package com.scentstation.notification.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

public record MarkNotificationsReadRequest(
    @NotEmpty @Size(max = 100) List<@NotNull @Positive Long> notificationIds
) { }
