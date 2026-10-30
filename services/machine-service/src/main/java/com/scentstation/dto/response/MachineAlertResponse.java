package com.scentstation.dto.response;

import com.scentstation.entity.MachineAlert;

import java.time.Instant;

public class MachineAlertResponse {
    private final Long id;
    private final String type;
    private final String severity;
    private final String status;
    private final String source;
    private final String tenantId;
    private final Long machineId;
    private final String locationId;
    private final Long slotId;
    private final String summary;
    private final Instant firstSeenAt;
    private final Instant lastSeenAt;
    private final Instant acknowledgedAt;
    private final String acknowledgedBy;
    private final Instant assignedAt;
    private final String assignedTo;
    private final Instant resolvedAt;
    private final String resolvedBy;
    private final String resolutionNotes;
    private final Instant escalatedAt;
    private final int reopenCount;
    private final Long responseTimeMillis;
    private final Long resolutionTimeMillis;

    public MachineAlertResponse(MachineAlert alert) {
        id = alert.getId();
        type = alert.getType().name();
        severity = alert.getSeverity().name();
        status = alert.getStatus().name();
        source = alert.getSource().name();
        tenantId = alert.getTenantId();
        machineId = alert.getMachineId();
        locationId = alert.getLocationId();
        slotId = alert.getSlotId();
        summary = alert.getSummary();
        firstSeenAt = alert.getFirstSeenAt();
        lastSeenAt = alert.getLastSeenAt();
        acknowledgedAt = alert.getAcknowledgedAt();
        acknowledgedBy = alert.getAcknowledgedBy();
        assignedAt = alert.getAssignedAt();
        assignedTo = alert.getAssignedTo();
        resolvedAt = alert.getResolvedAt();
        resolvedBy = alert.getResolvedBy();
        resolutionNotes = alert.getResolutionNotes();
        escalatedAt = alert.getEscalatedAt();
        reopenCount = alert.getReopenCount();
        responseTimeMillis = alert.getResponseTimeMillis();
        resolutionTimeMillis = alert.getResolutionTimeMillis();
    }

    public Long getId() { return id; }
    public String getType() { return type; }
    public String getSeverity() { return severity; }
    public String getStatus() { return status; }
    public String getSource() { return source; }
    public String getTenantId() { return tenantId; }
    public Long getMachineId() { return machineId; }
    public String getLocationId() { return locationId; }
    public Long getSlotId() { return slotId; }
    public String getSummary() { return summary; }
    public Instant getFirstSeenAt() { return firstSeenAt; }
    public Instant getLastSeenAt() { return lastSeenAt; }
    public Instant getAcknowledgedAt() { return acknowledgedAt; }
    public String getAcknowledgedBy() { return acknowledgedBy; }
    public Instant getAssignedAt() { return assignedAt; }
    public String getAssignedTo() { return assignedTo; }
    public Instant getResolvedAt() { return resolvedAt; }
    public String getResolvedBy() { return resolvedBy; }
    public String getResolutionNotes() { return resolutionNotes; }
    public Instant getEscalatedAt() { return escalatedAt; }
    public int getReopenCount() { return reopenCount; }
    public Long getResponseTimeMillis() { return responseTimeMillis; }
    public Long getResolutionTimeMillis() { return resolutionTimeMillis; }
}
