package com.scentstation.order.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import com.scentstation.order.entity.OrderType;

public record CreateOrderRequest(
        @Schema(description = "ID of the customer placing the order", example = "7")
        @NotNull @Positive Long customerId,
        @Schema(description = "Numeric Identity tenant ID; must match the authenticated principal", example = "1")
        @NotBlank String tenantId,
        @Schema(description = "Canonical positive numeric ID of the dispensing machine", example = "10")
        @NotBlank String machineId,
        @Schema(description = "Canonical positive numeric slot ID", example = "20")
        @NotBlank String slotId,
        @Schema(description = "At least one product item is required")
        @NotEmpty List<@NotNull @Valid OrderItemRequest> items,
        @Schema(description = "TRIAL triggers a dispenser after payment; BOTTLE creates a retail sale", example = "TRIAL")
        OrderType orderType
) {
    public OrderType normalizedOrderType() {
        return orderType == null ? OrderType.TRIAL : orderType;
    }
}
