package com.scentstation.order.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record CreateOrderRequest(
        @NotNull @Positive Long customerId,
        @NotBlank String machineId,
        @NotBlank String slotId,
        @NotEmpty List<@NotNull @Valid OrderItemRequest> items
) {
}
