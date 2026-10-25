package com.scentstation.order.entity;

import java.time.LocalDateTime;

public class DispensingCommand {

    private final String commandId;
    private final Long orderId;
    private final String paymentReference;
    private final String machineId;
    private final String slotId;
    private final LocalDateTime createdAt;
    private DispensingStatus status;
    private LocalDateTime resultReceivedAt;
    private boolean manualReviewRequired;
    private String manualReviewReason;

    public DispensingCommand(
            String commandId,
            Long orderId,
            String paymentReference,
            String machineId,
            String slotId
    ) {
        this.commandId = commandId;
        this.orderId = orderId;
        this.paymentReference = paymentReference;
        this.machineId = machineId;
        this.slotId = slotId;
        this.createdAt = LocalDateTime.now();
        this.status = DispensingStatus.REQUESTED;
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
        manualReviewRequired = result == DispensingStatus.UNKNOWN;
        manualReviewReason = manualReviewRequired ? reason : null;
    }
}
