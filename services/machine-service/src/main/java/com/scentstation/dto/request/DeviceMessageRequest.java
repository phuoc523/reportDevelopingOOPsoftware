package com.scentstation.dto.request;

import java.math.BigDecimal;
import java.time.Instant;

public class DeviceMessageRequest {
    private String eventId;
    private String deviceIdentity;
    private String tenantId;
    private Instant occurredAt;
    private String commandId;
    private String eventType;
    private String details;
    private String firmwareVersion;
    private String configurationVersion;
    private BigDecimal perfumeLevelPercent;
    private BigDecimal perfumeWeightGrams;
    private Boolean doorOpen;
    private Boolean leakDetected;
    private String actuatorStatus;
    private String sensorStatus;
    private String powerStatus;
    private Long slotId;
    private Boolean success;

    public DeviceMessageRequest() {
    }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public String getDeviceIdentity() { return deviceIdentity; }
    public void setDeviceIdentity(String deviceIdentity) { this.deviceIdentity = deviceIdentity; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }
    public String getCommandId() { return commandId; }
    public void setCommandId(String commandId) { this.commandId = commandId; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
    public String getFirmwareVersion() { return firmwareVersion; }
    public void setFirmwareVersion(String firmwareVersion) { this.firmwareVersion = firmwareVersion; }
    public String getConfigurationVersion() { return configurationVersion; }
    public void setConfigurationVersion(String configurationVersion) { this.configurationVersion = configurationVersion; }
    public BigDecimal getPerfumeLevelPercent() { return perfumeLevelPercent; }
    public void setPerfumeLevelPercent(BigDecimal perfumeLevelPercent) { this.perfumeLevelPercent = perfumeLevelPercent; }
    public BigDecimal getPerfumeWeightGrams() { return perfumeWeightGrams; }
    public void setPerfumeWeightGrams(BigDecimal perfumeWeightGrams) { this.perfumeWeightGrams = perfumeWeightGrams; }
    public Boolean getDoorOpen() { return doorOpen; }
    public void setDoorOpen(Boolean doorOpen) { this.doorOpen = doorOpen; }
    public Boolean getLeakDetected() { return leakDetected; }
    public void setLeakDetected(Boolean leakDetected) { this.leakDetected = leakDetected; }
    public String getActuatorStatus() { return actuatorStatus; }
    public void setActuatorStatus(String actuatorStatus) { this.actuatorStatus = actuatorStatus; }
    public String getSensorStatus() { return sensorStatus; }
    public void setSensorStatus(String sensorStatus) { this.sensorStatus = sensorStatus; }
    public String getPowerStatus() { return powerStatus; }
    public void setPowerStatus(String powerStatus) { this.powerStatus = powerStatus; }
    public Long getSlotId() { return slotId; }
    public void setSlotId(Long slotId) { this.slotId = slotId; }
    public Boolean getSuccess() { return success; }
    public void setSuccess(Boolean success) { this.success = success; }
}
