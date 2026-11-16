package com.scentstation.order.dto.response;

import com.scentstation.order.entity.DispensingStatus;

import java.time.LocalDateTime;

public record DispensingResponse(
        Long orderId,
        String commandId,
        String machineId,
        String slotId,
        DispensingStatus status,
        boolean manualReviewRequired,
        String manualReviewReason,
        LocalDateTime createdAt,
        LocalDateTime resultReceivedAt
) {
}
