package com.scentstation.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "dispensing_commands")
public class DispensingCommand {

    @Id
    @Column(name = "command_id", length = 36)
    private String commandId;
    @Column(name = "order_id", nullable = false, unique = true)
    private Long orderId;
    @Column(name = "payment_reference", nullable = false, length = 64)
    private String paymentReference;
    @Column(name = "machine_id", nullable = false, length = 100)
    private String machineId;
    @Column(name = "slot_id", nullable = false, length = 100)
    private String slotId;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DispensingStatus status;
    @Column(name = "result_received_at")
    private LocalDateTime resultReceivedAt;
    @Column(name = "manual_review_required", nullable = false)
    private boolean manualReviewRequired;
    @Column(name = "manual_review_reason", length = 1000)
    private String manualReviewReason;

    protected DispensingCommand() {
    }

    public DispensingCommand(
            String commandId,
            Long orderId,
            String paymentReference,
            String machineId,
            String slotId
    ) {
        this(commandId, orderId, paymentReference, machineId, slotId, LocalDateTime.now(),
                DispensingStatus.REQUESTED, null, false, null);
    }

    public DispensingCommand(
            String commandId,
            Long orderId,
            String paymentReference,
            String machineId,
            String slotId,
            LocalDateTime createdAt,
            DispensingStatus status,
            LocalDateTime resultReceivedAt,
            boolean manualReviewRequired,
            String manualReviewReason
    ) {
        this.commandId = commandId;
        this.orderId = orderId;
        this.paymentReference = paymentReference;
        this.machineId = machineId;
        this.slotId = slotId;
        this.createdAt = createdAt;
        this.status = status;
        this.resultReceivedAt = resultReceivedAt;
        this.manualReviewRequired = manualReviewRequired;
        this.manualReviewReason = manualReviewReason;
    }

    public String getCommandId() {
        return commandId;
    }

    public Long getOrderId() {
        return orderId;
    }

    public String getPaymentReference() {
        return paymentReference;
    }

    public String getMachineId() {
        return machineId;
    }

    public String getSlotId() {
        return slotId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public synchronized DispensingStatus getStatus() {
        return status;
    }

    public synchronized LocalDateTime getResultReceivedAt() {
        return resultReceivedAt;
    }

    public synchronized boolean isManualReviewRequired() {
        return manualReviewRequired;
    }

    public synchronized String getManualReviewReason() {
        return manualReviewReason;
    }

    public synchronized void recordResult(DispensingStatus result, String reason) {
        if (status == DispensingStatus.DISPENSED || status == DispensingStatus.FAILED) {
            return;
        }
        if (status == DispensingStatus.PROCESSING && result == DispensingStatus.ACCEPTED) {
            return;
        }
        status = result;
        resultReceivedAt = LocalDateTime.now();
        manualReviewRequired = result == DispensingStatus.UNKNOWN || result == DispensingStatus.FAILED;
        manualReviewReason = manualReviewRequired ? reason : null;
    }
}
