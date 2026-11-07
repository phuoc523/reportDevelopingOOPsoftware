package com.scentstation.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment_callback_events")
public class PaymentCallbackEvent {

    public enum Outcome {
        ACCEPTED,
        REJECTED
    }

    @Id
    @Column(name = "event_id", length = 255)
    private String eventId;
    @Column(nullable = false, length = 64)
    private String reference;
    @Column(name = "order_id", nullable = false)
    private Long orderId;
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;
    @Column(nullable = false, length = 3, columnDefinition = "CHAR(3)")
    private String currency;
    @Column(name = "payment_status", nullable = false, length = 32)
    private String status;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Outcome outcome;
    @Column(name = "error_status", nullable = false)
    private int errorStatus;
    @Column(nullable = false, length = 1000)
    private String message;
    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;

    protected PaymentCallbackEvent() {
    }

    public PaymentCallbackEvent(String eventId, String reference, Long orderId, BigDecimal amount, String currency,
                                String status, Outcome outcome, int errorStatus, String message,
                                LocalDateTime receivedAt) {
        this.eventId = eventId;
        this.reference = reference;
        this.orderId = orderId;
        this.amount = amount;
        this.currency = currency;
        this.status = status;
        this.outcome = outcome;
        this.errorStatus = errorStatus;
        this.message = message;
        this.receivedAt = receivedAt;
    }

    public String eventId() {
        return eventId;
    }

    public String reference() {
        return reference;
    }

    public Long orderId() {
        return orderId;
    }

    public BigDecimal amount() {
        return amount;
    }

    public String currency() {
        return currency;
    }

    public String status() {
        return status;
    }

    public Outcome outcome() {
        return outcome;
    }

    public int errorStatus() {
        return errorStatus;
    }

    public String message() {
        return message;
    }

    public LocalDateTime receivedAt() {
        return receivedAt;
    }

    public boolean matches(String callbackReference, Long callbackOrderId, BigDecimal callbackAmount,
                           String callbackCurrency, String callbackStatus) {
        return reference.equals(callbackReference)
                && orderId.equals(callbackOrderId)
                && amount.compareTo(callbackAmount) == 0
                && currency.equals(callbackCurrency)
                && status.equals(callbackStatus);
    }
}
