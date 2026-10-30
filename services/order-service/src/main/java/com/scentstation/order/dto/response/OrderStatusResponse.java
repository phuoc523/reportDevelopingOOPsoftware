package com.scentstation.order.dto.response;

import com.scentstation.order.entity.OrderStatus;

import java.time.LocalDateTime;

public record OrderStatusResponse(
        Long orderId,
        OrderStatus status,
        LocalDateTime updatedAt
) {
}
