package com.scentstation.dto.response;

import com.scentstation.entity.MachineDeviceEvent;

import java.time.Instant;

public class MachineDeviceEventResponse {
    private final Long id;
    private final String eventId;
    private final Long machineId;
    private final String tenantId;
    private final String locationId;
    private final String deviceIdentity;
    private final String commandId;
    private final String eventType;
    private final String details;
    private final String firmwareVersion;
    private final String configurationVersion;
    private final Instant occurredAt;
    private final Instant receivedAt;

    public MachineDeviceEventResponse(MachineDeviceEvent event) {
        id = event.getId();
        eventId = event.getEventId();
        machineId = event.getMachineId();
        tenantId = event.getTenantId();
        locationId = event.getLocationId();
        deviceIdentity = event.getDeviceIdentity();
        commandId = event.getCommandId();
        eventType = event.getEventType().name();
        details = event.getDetails();
        firmwareVersion = event.getFirmwareVersion();
        configurationVersion = event.getConfigurationVersion();
        occurredAt = event.getOccurredAt();
        receivedAt = event.getReceivedAt();
    }

    public Long getId() { return id; }
    public String getEventId() { return eventId; }
    public Long getMachineId() { return machineId; }
    public String getTenantId() { return tenantId; }
    public String getLocationId() { return locationId; }
    public String getDeviceIdentity() { return deviceIdentity; }
    public String getCommandId() { return commandId; }
    public String getEventType() { return eventType; }
    public String getDetails() { return details; }
    public String getFirmwareVersion() { return firmwareVersion; }
    public String getConfigurationVersion() { return configurationVersion; }
    public Instant getOccurredAt() { return occurredAt; }
    public Instant getReceivedAt() { return receivedAt; }
}
