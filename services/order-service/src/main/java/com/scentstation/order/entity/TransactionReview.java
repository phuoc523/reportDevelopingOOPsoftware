package com.scentstation.order.entity;

import java.time.LocalDateTime;

public class TransactionReview {

    private final String reference;
    private final Long orderId;
    private final LocalDateTime createdAt;
    private String reason;
    private TransactionReviewStatus status;
    private LocalDateTime resolvedAt;

    public TransactionReview(String reference, Long orderId, String reason) {
        this.reference = reference;
        this.orderId = orderId;
        this.reason = reason;
        this.status = TransactionReviewStatus.OPEN;
        this.createdAt = LocalDateTime.now();
    }

    public synchronized void updateReason(String newReason) {
        if (status == TransactionReviewStatus.OPEN && !reason.contains(newReason)) {
            reason = reason + "; " + newReason;
        }
    }

    public synchronized void resolve() {
        if (status == TransactionReviewStatus.OPEN) {
            status = TransactionReviewStatus.RESOLVED;
            resolvedAt = LocalDateTime.now();
        }
    }

    public String getReference() {
        return reference;
    }

    public Long getOrderId() {
        return orderId;
    }

    public synchronized String getReason() {
        return reason;
    }

    public synchronized TransactionReviewStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public synchronized LocalDateTime getResolvedAt() {
        return resolvedAt;
    }
}
