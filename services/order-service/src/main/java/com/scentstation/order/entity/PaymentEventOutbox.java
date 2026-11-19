package com.scentstation.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment_event_outbox")
public class PaymentEventOutbox {
    @Id
    @Column(name = "event_id", length = 100)
    private String eventId;

    @Column(name = "event_version", nullable = false)
    private int eventVersion;

    @Column(name = "payment_reference", nullable = false, length = 64)
    private String reference;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "tenant_id", length = 100)
    private String tenantId;

    @Column(name = "machine_id", length = 100)
    private String machineId;

    @Column(name = "slot_id", length = 100)
    private String slotId;

    @Column(name = "product_id")
    private Long productId;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, length = 3, columnDefinition = "CHAR(3)")
    private String currency;

    @Column(name = "payment_status", nullable = false, length = 32)
    private String status;

    @Column(name = "payment_created_at", nullable = false)
    private LocalDateTime paymentCreatedAt;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_attempt_at", nullable = false)
    private LocalDateTime nextAttemptAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected PaymentEventOutbox() {
    }

    public PaymentEventOutbox(Payment payment) {
        this(payment, null);
    }

    public PaymentEventOutbox(Payment payment, Order order) {
        this.eventId = payment.getReference() + ":" + payment.getStatus().name();
        this.eventVersion = 1;
        this.reference = payment.getReference();
        this.orderId = payment.getOrderId();
        if (order != null) {
            this.tenantId = order.getTenantId();
            this.machineId = order.getMachineId();
            this.slotId = order.getSlotId();
            this.productId = order.getItems().isEmpty() ? null : order.getItems().get(0).productId();
        }
        this.amount = payment.getAmount();
        this.currency = payment.getCurrency();
        this.status = payment.getStatus().name();
        this.paymentCreatedAt = payment.getCreatedAt();
        this.paidAt = payment.getPaidAt();
        this.occurredAt = LocalDateTime.now();
        this.createdAt = occurredAt;
        this.nextAttemptAt = occurredAt;
    }

    public void attempted(LocalDateTime retryAt) {
        attemptCount++;
        nextAttemptAt = retryAt;
    }

    public String getEventId() { return eventId; }
    public int getEventVersion() { return eventVersion; }
    public String getReference() { return reference; }
    public Long getOrderId() { return orderId; }
    public String getTenantId() { return tenantId; }
    public String getMachineId() { return machineId; }
    public String getSlotId() { return slotId; }
    public Long getProductId() { return productId; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getStatus() { return status; }
    public LocalDateTime getPaymentCreatedAt() { return paymentCreatedAt; }
    public LocalDateTime getPaidAt() { return paidAt; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
    public int getAttemptCount() { return attemptCount; }
    public LocalDateTime getNextAttemptAt() { return nextAttemptAt; }
}
