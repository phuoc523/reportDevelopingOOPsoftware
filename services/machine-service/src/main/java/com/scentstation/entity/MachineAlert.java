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
@Table(name = "machine_alerts", indexes = {
    @Index(name = "idx_alert_tenant_status_time", columnList = "tenant_id, status, last_seen_at"),
    @Index(name = "idx_alert_machine_status", columnList = "machine_id, status"),
    @Index(name = "idx_alert_dedupe_key", columnList = "dedupe_key, status")
})
public class MachineAlert {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dedupe_key", nullable = false, length = 250)
    private String dedupeKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "alert_type", nullable = false, length = 40)
    private MachineAlertType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MachineAlertSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MachineAlertStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "alert_source", nullable = false, length = 20)
    private MachineAlertSource source;

    @Column(name = "tenant_id", nullable = false, length = 100)
    private String tenantId;

    @Column(name = "machine_id", nullable = false)
    private Long machineId;

    @Column(name = "location_id", nullable = false, length = 100)
    private String locationId;

    @Column(name = "slot_id")
    private Long slotId;

    @Column(nullable = false, length = 500)
    private String summary;

    @Column(name = "first_seen_at", nullable = false)
    private Instant firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @Column(name = "acknowledged_by", length = 100)
    private String acknowledgedBy;

    @Column(name = "assigned_at")
    private Instant assignedAt;

    @Column(name = "assigned_to", length = 100)
    private String assignedTo;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolved_by", length = 100)
    private String resolvedBy;

    @Column(name = "resolution_notes", length = 1000)
    private String resolutionNotes;

    @Column(name = "escalated_at")
    private Instant escalatedAt;

    @Column(name = "reopen_count", nullable = false)
    private int reopenCount;

    protected MachineAlert() {
    }

    public MachineAlert(String dedupeKey, MachineAlertType type, MachineAlertSeverity severity,
                        MachineAlertSource source,
                        Machine machine, Long slotId, String summary, Instant occurredAt) {
        this.dedupeKey = dedupeKey;
        this.type = type;
        this.severity = severity;
        this.status = MachineAlertStatus.OPEN;
        this.source = source;
        this.tenantId = machine.getTenantId();
        this.machineId = machine.getId();
        this.locationId = machine.getLocationId();
        this.slotId = slotId;
        this.summary = summary;
        this.firstSeenAt = occurredAt;
        this.lastSeenAt = occurredAt;
    }

    public boolean observeAgain(MachineAlertSeverity observedSeverity, String observedSummary,
                                Instant occurredAt) {
        boolean changed = false;
        lastSeenAt = occurredAt;
        if (observedSeverity.ordinal() > severity.ordinal()) {
            severity = observedSeverity;
            changed = true;
        }
        if (observedSummary != null && !observedSummary.isBlank()
                && !observedSummary.equals(summary)) {
            summary = observedSummary;
            changed = true;
        }
        return changed;
    }

    public void acknowledge(String actorId, Instant now) {
        if (acknowledgedAt == null) {
            acknowledgedAt = now;
            acknowledgedBy = actorId;
        }
        if (status == MachineAlertStatus.OPEN || status == MachineAlertStatus.ESCALATED) {
            status = MachineAlertStatus.ACKNOWLEDGED;
        }
    }

    public void assign(String assigneeId, Instant now) {
        assignedTo = assigneeId;
        assignedAt = now;
        status = MachineAlertStatus.ASSIGNED;
    }

    public void resolve(String actorId, String notes, Instant now) {
        resolvedBy = actorId;
        resolutionNotes = notes;
        resolvedAt = now;
        status = MachineAlertStatus.RESOLVED;
    }

    public void reopen(String notes, Instant now) {
        reopenCount++;
        resolutionNotes = notes;
        resolvedAt = null;
        resolvedBy = null;
        acknowledgedAt = null;
        acknowledgedBy = null;
        assignedAt = null;
        assignedTo = null;
        escalatedAt = null;
        status = MachineAlertStatus.OPEN;
        lastSeenAt = now;
    }

    public void escalate(Instant now) {
        if (status != MachineAlertStatus.RESOLVED && escalatedAt == null) {
            escalatedAt = now;
            status = MachineAlertStatus.ESCALATED;
        }
    }

    public boolean isActive() {
        return status != MachineAlertStatus.RESOLVED;
    }

    public Long getResponseTimeMillis() {
        return acknowledgedAt == null ? null : acknowledgedAt.toEpochMilli() - firstSeenAt.toEpochMilli();
    }

    public Long getResolutionTimeMillis() {
        return resolvedAt == null ? null : resolvedAt.toEpochMilli() - firstSeenAt.toEpochMilli();
    }

    public Long getId() { return id; }
    public String getDedupeKey() { return dedupeKey; }
    public MachineAlertType getType() { return type; }
    public MachineAlertSeverity getSeverity() { return severity; }
    public MachineAlertStatus getStatus() { return status; }
    public MachineAlertSource getSource() { return source; }
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
}
