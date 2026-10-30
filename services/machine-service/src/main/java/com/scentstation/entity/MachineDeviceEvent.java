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
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(name = "machine_device_events", uniqueConstraints =
    @UniqueConstraint(name = "uk_machine_device_event", columnNames = "event_id"), indexes = {
        @Index(name = "idx_device_event_machine_time", columnList = "machine_id, occurred_at"),
        @Index(name = "idx_device_event_tenant_time", columnList = "tenant_id, occurred_at"),
        @Index(name = "idx_device_event_occurred_at", columnList = "occurred_at")
    })
public class MachineDeviceEvent {
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

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 40)
    private DeviceEventType eventType;

    @Column(length = 1000)
    private String details;

    @Column(name = "firmware_version", length = 100)
    private String firmwareVersion;

    @Column(name = "configuration_version", length = 100)
    private String configurationVersion;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    protected MachineDeviceEvent() {
    }

    public MachineDeviceEvent(String eventId, Machine machine, String commandId,
                              DeviceEventType eventType, String details, String firmwareVersion,
                              String configurationVersion, Instant occurredAt) {
        this.eventId = eventId;
        this.machineId = machine.getId();
        this.tenantId = machine.getTenantId();
        this.locationId = machine.getLocationId();
        this.deviceIdentity = machine.getDeviceIdentity();
        this.commandId = commandId;
        this.eventType = eventType;
        this.details = details;
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
    public DeviceEventType getEventType() { return eventType; }
    public String getDetails() { return details; }
    public String getFirmwareVersion() { return firmwareVersion; }
    public String getConfigurationVersion() { return configurationVersion; }
    public Instant getOccurredAt() { return occurredAt; }
    public Instant getReceivedAt() { return receivedAt; }
}
