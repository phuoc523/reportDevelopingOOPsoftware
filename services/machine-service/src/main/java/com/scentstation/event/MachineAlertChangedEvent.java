package com.scentstation.event;

import java.time.Instant;
import java.util.UUID;

public class MachineAlertChangedEvent {
    private final String eventId;
    private final Long alertId;
    private final String tenantId;
    private final Long machineId;
    private final String locationId;
    private final String alertType;
    private final String severity;
    private final String status;
    private final Instant occurredAt;
    private final String source;

    public MachineAlertChangedEvent(Long alertId, String tenantId, Long machineId,
                                    String locationId, String alertType, String severity,
                                    String status, Instant occurredAt) {
        this.eventId = UUID.randomUUID().toString();
        this.alertId = alertId;
        this.tenantId = tenantId;
        this.machineId = machineId;
        this.locationId = locationId;
        this.alertType = alertType;
        this.severity = severity;
        this.status = status;
        this.occurredAt = occurredAt;
        this.source = "machine-service";
    }

    public String getEventId() { return eventId; }
    public Long getAlertId() { return alertId; }
    public String getTenantId() { return tenantId; }
    public Long getMachineId() { return machineId; }
    public String getLocationId() { return locationId; }
    public String getAlertType() { return alertType; }
    public String getSeverity() { return severity; }
    public String getStatus() { return status; }
    public Instant getOccurredAt() { return occurredAt; }
    public String getSource() { return source; }
}
