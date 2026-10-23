package com.scentstation.order.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

public class Payment {

    public static final String CURRENCY = "VND";

    private final String reference;
    private final Long orderId;
    private final BigDecimal amount;
    private final String currency;
    private final String qrPayload;
    private final LocalDateTime createdAt;
    private final LocalDateTime expiresAt;
    private volatile PaymentStatus status;
    private LocalDateTime paidAt;

    public Payment(String reference, Long orderId, BigDecimal amount, String qrPayload) {
        this(reference, orderId, amount, CURRENCY, qrPayload, LocalDateTime.now(),
                LocalDateTime.now().plus(15, ChronoUnit.MINUTES));
    }

    public Payment(
            String reference,
            Long orderId,
            BigDecimal amount,
            String currency,
            String qrPayload,
            LocalDateTime createdAt,
            LocalDateTime expiresAt
    ) {
        this.reference = reference;
        this.orderId = orderId;
        this.amount = amount;
        this.currency = currency;
        this.qrPayload = qrPayload;
        this.status = PaymentStatus.PENDING;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
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

    public String getCurrency() {
        return currency;
    }

    public String getQrPayload() {
        return qrPayload;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
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

    public void markFailed() {
        if (status == PaymentStatus.PENDING) {
            status = PaymentStatus.FAILED;
        }
    }

    public void markExpired() {
        if (status == PaymentStatus.PENDING) {
            status = PaymentStatus.EXPIRED;
        }
    }

    public boolean isExpiredAt(LocalDateTime time) {
        return status == PaymentStatus.PENDING && !time.isBefore(expiresAt);
    }
}
