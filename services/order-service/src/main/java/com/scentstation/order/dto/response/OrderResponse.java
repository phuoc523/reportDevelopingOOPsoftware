package com.scentstation.order.dto.response;

import com.scentstation.order.entity.DispensingStatus;
import com.scentstation.order.entity.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderResponse(
        Long id,
        Long customerId,
        String machineId,
        String slotId,
        List<OrderItemResponse> items,
        BigDecimal totalAmount,
        OrderStatus status,
        String commandId,
        DispensingStatus dispensingStatus,
        boolean manualReviewRequired,
        String manualReviewReason,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
