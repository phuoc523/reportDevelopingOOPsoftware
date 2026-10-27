package com.scentstation.dto.response;

import com.scentstation.entity.MachineTelemetry;

import java.math.BigDecimal;
import java.time.Instant;

public class MachineTelemetryResponse {
    private final Long id;
    private final String eventId;
    private final Long machineId;
    private final String tenantId;
    private final String locationId;
    private final String deviceIdentity;
    private final String commandId;
    private final Long slotId;
    private final BigDecimal perfumeLevelPercent;
    private final BigDecimal perfumeWeightGrams;
    private final Boolean doorOpen;
    private final Boolean leakDetected;
    private final String actuatorStatus;
    private final String sensorStatus;
    private final String powerStatus;
    private final String firmwareVersion;
    private final String configurationVersion;
    private final Instant occurredAt;
    private final Instant receivedAt;

    public MachineTelemetryResponse(MachineTelemetry telemetry) {
        id = telemetry.getId();
        eventId = telemetry.getEventId();
        machineId = telemetry.getMachineId();
        tenantId = telemetry.getTenantId();
        locationId = telemetry.getLocationId();
        deviceIdentity = telemetry.getDeviceIdentity();
        commandId = telemetry.getCommandId();
        slotId = telemetry.getSlotId();
        perfumeLevelPercent = telemetry.getPerfumeLevelPercent();
        perfumeWeightGrams = telemetry.getPerfumeWeightGrams();
        doorOpen = telemetry.getDoorOpen();
        leakDetected = telemetry.getLeakDetected();
        actuatorStatus = telemetry.getActuatorStatus();
        sensorStatus = telemetry.getSensorStatus();
        powerStatus = telemetry.getPowerStatus();
        firmwareVersion = telemetry.getFirmwareVersion();
        configurationVersion = telemetry.getConfigurationVersion();
        occurredAt = telemetry.getOccurredAt();
        receivedAt = telemetry.getReceivedAt();
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
