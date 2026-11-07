package com.scentstation.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "transaction_reviews")
public class TransactionReview {

    @Id
    @Column(length = 64)
    private String reference;
    @Column(name = "order_id")
    private Long orderId;
    @Column(nullable = false, length = 2000)
    private String reason;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TransactionReviewStatus status;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    protected TransactionReview() {
    }

    public TransactionReview(String reference, Long orderId, String reason) {
        this(reference, orderId, reason, TransactionReviewStatus.OPEN, LocalDateTime.now(), null);
    }

    public TransactionReview(
            String reference,
            Long orderId,
            String reason,
            TransactionReviewStatus status,
            LocalDateTime createdAt,
            LocalDateTime resolvedAt
    ) {
        this.reference = reference;
        this.orderId = orderId;
        this.reason = reason;
        this.status = status;
        this.createdAt = createdAt;
        this.resolvedAt = resolvedAt;
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
