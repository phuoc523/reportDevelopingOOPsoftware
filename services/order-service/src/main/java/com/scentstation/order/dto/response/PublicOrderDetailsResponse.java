package com.scentstation.order.dto.response;

import com.scentstation.order.entity.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record PublicOrderDetailsResponse(
        Long id,
        String machineId,
        String slotId,
        List<OrderItemResponse> items,
        BigDecimal totalAmount,
        OrderStatus status,
        String currency,
        LocalDateTime expiresAt
) {
}
