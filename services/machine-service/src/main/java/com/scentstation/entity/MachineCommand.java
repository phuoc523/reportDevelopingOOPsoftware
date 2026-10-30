package com.scentstation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "machine_commands", indexes = {
    @Index(name = "idx_command_machine_issued", columnList = "machine_id, issued_at"),
    @Index(name = "idx_command_status_expiry", columnList = "status, expires_at")
})
public class MachineCommand {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "command_id", nullable = false, unique = true, length = 100)
    private String commandId;

    @Column(name = "machine_id", nullable = false)
    private Long machineId;

    @Column(name = "tenant_id", nullable = false, length = 100)
    private String tenantId;

    @Column(name = "order_id", nullable = false, length = 100)
    private String orderId;

    @Column(name = "slot_id", nullable = false, length = 100)
    private String slotId;

    @Column(name = "payment_reference", nullable = false, length = 100)
    private String paymentReference;

    @Column(name = "request_fingerprint", nullable = false, length = 64)
    private String requestFingerprint;

    @Column(name = "device_identity", length = 150)
    private String deviceIdentity;

    @Column(name = "command_type", nullable = false, length = 50)
    private String commandType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MachineCommandStatus status;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "result_success")
    private Boolean resultSuccess;

    @Column(name = "result_details", length = 500)
    private String resultDetails;

    protected MachineCommand() {
    }

    public MachineCommand(String commandId, Machine machine, String commandType,
                          Instant issuedAt, Instant expiresAt) {
        this.commandId = commandId;
        this.machineId = machine.getId();
        this.tenantId = machine.getTenantId();
        this.deviceIdentity = machine.getDeviceIdentity();
        this.commandType = commandType;
        this.orderId = null;
        this.slotId = null;
        this.paymentReference = null;
        this.requestFingerprint = null;
        this.status = MachineCommandStatus.ACCEPTED;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
    }

    public MachineCommand(String commandId, String tenantId, String orderId, String paymentReference,
                          Long machineId, String slotId, String requestFingerprint,
                          Instant issuedAt, Instant expiresAt) {
        this.commandId = commandId;
        this.tenantId = tenantId;
        this.machineId = machineId;
        this.orderId = orderId;
        this.paymentReference = paymentReference;
        this.slotId = slotId;
        this.requestFingerprint = requestFingerprint;
        this.commandType = "SPRAY";
        this.status = MachineCommandStatus.ACCEPTED;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
    }

    public void acknowledge(Instant at) {
        if (isTerminal()) {
            return;
        }
        if (at.isAfter(expiresAt)) {
            status = MachineCommandStatus.EXPIRED;
            completedAt = at;
            return;
        }
        if (acknowledgedAt == null) {
            acknowledgedAt = at;
        }
        status = MachineCommandStatus.ACKNOWLEDGED;
    }

    public void complete(boolean success, String details, Instant at) {
        if (isTerminal()) {
            return;
        }
        resultSuccess = success;
        resultDetails = details;
        completedAt = at;
        status = at.isAfter(expiresAt) ? MachineCommandStatus.EXPIRED
            : success ? MachineCommandStatus.DISPENSED : MachineCommandStatus.FAILED;
    }

    private boolean isTerminal() {
        return status == MachineCommandStatus.DISPENSED
            || status == MachineCommandStatus.FAILED
            || status == MachineCommandStatus.EXPIRED;
    }

    public Long getId() { return id; }
    public String getCommandId() { return commandId; }
    public Long getMachineId() { return machineId; }
    public String getTenantId() { return tenantId; }
    public String getOrderId() { return orderId; }
    public String getSlotId() { return slotId; }
    public String getPaymentReference() { return paymentReference; }
    public String getRequestFingerprint() { return requestFingerprint; }
    public String getDeviceIdentity() { return deviceIdentity; }
    public String getCommandType() { return commandType; }
    public MachineCommandStatus getStatus() { return status; }
    public Instant getIssuedAt() { return issuedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getAcknowledgedAt() { return acknowledgedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public Boolean getResultSuccess() { return resultSuccess; }
    public String getResultDetails() { return resultDetails; }
}
