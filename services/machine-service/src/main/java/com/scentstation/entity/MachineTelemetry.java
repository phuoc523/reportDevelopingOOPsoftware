package com.scentstation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "machine_telemetry", uniqueConstraints =
    @UniqueConstraint(name = "uk_machine_telemetry_event", columnNames = "event_id"), indexes = {
        @Index(name = "idx_telemetry_machine_time", columnList = "machine_id, occurred_at"),
        @Index(name = "idx_telemetry_tenant_time", columnList = "tenant_id, occurred_at"),
        @Index(name = "idx_telemetry_occurred_at", columnList = "occurred_at")
    })
public class MachineTelemetry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, length = 100)
    private String eventId;

    @Column(name = "machine_id", nullable = false)
    private Long machineId;

    @Column(name = "tenant_id", nullable = false, length = 100)
    private String tenantId;

    @Column(name = "location_id", nullable = false, length = 100)
    private String locationId;

    @Column(name = "device_identity", nullable = false, length = 150)
    private String deviceIdentity;

    @Column(name = "command_id", length = 100)
    private String commandId;

    @Column(name = "slot_id")
    private Long slotId;

    @Column(name = "perfume_level_percent", precision = 5, scale = 2)
    private BigDecimal perfumeLevelPercent;

    @Column(name = "perfume_weight_grams", precision = 10, scale = 3)
    private BigDecimal perfumeWeightGrams;

    @Column(name = "door_open")
    private Boolean doorOpen;

    @Column(name = "leak_detected")
    private Boolean leakDetected;

    @Column(name = "actuator_status", length = 30)
    private String actuatorStatus;

    @Column(name = "sensor_status", length = 30)
    private String sensorStatus;

    @Column(name = "power_status", length = 30)
    private String powerStatus;

    @Column(name = "firmware_version", length = 100)
    private String firmwareVersion;

    @Column(name = "configuration_version", length = 100)
    private String configurationVersion;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    protected MachineTelemetry() {
    }

    public MachineTelemetry(String eventId, Machine machine, String commandId,
                            Long slotId,
                            BigDecimal perfumeLevelPercent, BigDecimal perfumeWeightGrams,
                            Boolean doorOpen, Boolean leakDetected, String actuatorStatus,
                            String sensorStatus, String powerStatus, String firmwareVersion,
                            String configurationVersion,
                            Instant occurredAt) {
        this.eventId = eventId;
        this.machineId = machine.getId();
        this.tenantId = machine.getTenantId();
        this.locationId = machine.getLocationId();
        this.deviceIdentity = machine.getDeviceIdentity();
        this.commandId = commandId;
        this.slotId = slotId;
        this.perfumeLevelPercent = perfumeLevelPercent;
        this.perfumeWeightGrams = perfumeWeightGrams;
        this.doorOpen = doorOpen;
        this.leakDetected = leakDetected;
        this.actuatorStatus = actuatorStatus;
        this.sensorStatus = sensorStatus;
        this.powerStatus = powerStatus;
        this.firmwareVersion = firmwareVersion;
        this.configurationVersion = configurationVersion;
        this.occurredAt = occurredAt;
        this.receivedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getEventId() { return eventId; }
    public Long getMachineId() { return machineId; }
    public String getTenantId() { return tenantId; }
    public String getLocationId() { return locationId; }
    public String getDeviceIdentity() { return deviceIdentity; }
    public String getCommandId() { return commandId; }
    public Long getSlotId() { return slotId; }
    public BigDecimal getPerfumeLevelPercent() { return perfumeLevelPercent; }
    public BigDecimal getPerfumeWeightGrams() { return perfumeWeightGrams; }
    public Boolean getDoorOpen() { return doorOpen; }
    public Boolean getLeakDetected() { return leakDetected; }
    public String getActuatorStatus() { return actuatorStatus; }
    public String getSensorStatus() { return sensorStatus; }
    public String getPowerStatus() { return powerStatus; }
    public String getFirmwareVersion() { return firmwareVersion; }
    public String getConfigurationVersion() { return configurationVersion; }
    public Instant getOccurredAt() { return occurredAt; }
    public Instant getReceivedAt() { return receivedAt; }
}
