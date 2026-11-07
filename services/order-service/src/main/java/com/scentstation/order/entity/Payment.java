package com.scentstation.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Entity
@Table(name = "payments")
public class Payment {

    public static final String CURRENCY = "VND";

    @Id
    @Column(length = 64)
    private String reference;
    @Column(name = "order_id", nullable = false, unique = true)
    private Long orderId;
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;
    @Column(nullable = false, length = 3, columnDefinition = "CHAR(3)")
    private String currency;
    @Column(name = "qr_payload", nullable = false, columnDefinition = "TEXT")
    private String qrPayload;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PaymentStatus status;
    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    protected Payment() {
    }

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
        this(reference, orderId, amount, currency, qrPayload, createdAt, expiresAt, PaymentStatus.PENDING, null);
    }

    public Payment(
            String reference,
            Long orderId,
            BigDecimal amount,
            String currency,
            String qrPayload,
            LocalDateTime createdAt,
            LocalDateTime expiresAt,
            PaymentStatus status,
            LocalDateTime paidAt
    ) {
        this.reference = reference;
        this.orderId = orderId;
        this.amount = amount;
        this.currency = currency;
        this.qrPayload = qrPayload;
        this.status = status;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.paidAt = paidAt;
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
