package com.scentstation.order.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class Payment {

    private final String reference;
    private final Long orderId;
    private final BigDecimal amount;
    private final String qrPayload;
    private final LocalDateTime createdAt;
    private volatile PaymentStatus status;
    private LocalDateTime paidAt;

    public Payment(String reference, Long orderId, BigDecimal amount, String qrPayload) {
        this.reference = reference;
        this.orderId = orderId;
        this.amount = amount;
        this.qrPayload = qrPayload;
        this.status = PaymentStatus.PENDING;
        this.createdAt = LocalDateTime.now();
    }

    public String getReference() {
        return reference;
    }

    public Long getOrderId() {
        return orderId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getQrPayload() {
        return qrPayload;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public LocalDateTime getPaidAt() {
        return paidAt;
    }

    public void markPaid() {
        if (status == PaymentStatus.PENDING) {
            paidAt = LocalDateTime.now();
            status = PaymentStatus.PAID;
        }
    }
}
