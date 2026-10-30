package com.scentstation.order.dto.response;

import com.scentstation.order.entity.TransactionReviewStatus;

import java.time.LocalDateTime;

public record TransactionReviewResponse(
        String reference,
        Long orderId,
        String reason,
        TransactionReviewStatus status,
        boolean manualReviewRequired,
        LocalDateTime createdAt,
        LocalDateTime resolvedAt
) {
}
