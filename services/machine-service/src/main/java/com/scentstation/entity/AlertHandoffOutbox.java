package com.scentstation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "alert_handoff_outbox")
public class AlertHandoffOutbox {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "target_service", nullable = false, length = 30)
    private String targetService;

    @Column(name = "event_id", nullable = false, length = 36)
    private String eventId;

    @Column(name = "alert_id", nullable = false)
    private Long alertId;

    @Column(name = "tenant_id", nullable = false, length = 100)
    private String tenantId;

    @Column(name = "machine_id", nullable = false)
    private Long machineId;

    @Column(name = "location_id", nullable = false, length = 100)
    private String locationId;

    @Column(name = "alert_type", nullable = false, length = 40)
    private String alertType;

    @Column(nullable = false, length = 20)
    private String severity;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AlertHandoffOutbox() {}

    public AlertHandoffOutbox(String targetService, String eventId, Long alertId, String tenantId,
                              Long machineId, String locationId, String alertType, String severity,
                              String status, Instant occurredAt) {
        this.targetService = targetService;
        this.eventId = eventId;
        this.alertId = alertId;
        this.tenantId = tenantId;
        this.machineId = machineId;
        this.locationId = locationId;
        this.alertType = alertType;
        this.severity = severity;
        this.status = status;
        this.occurredAt = occurredAt;
        this.attemptCount = 0;
        this.createdAt = Instant.now();
        this.nextAttemptAt = createdAt;
    }

    public void attempted(Instant nextAttemptAt) {
        attemptCount++;
        this.nextAttemptAt = nextAttemptAt;
    }

    public Long getId() { return id; }
    public String getTargetService() { return targetService; }
    public String getEventId() { return eventId; }
    public Long getAlertId() { return alertId; }
    public String getTenantId() { return tenantId; }
    public Long getMachineId() { return machineId; }
    public String getLocationId() { return locationId; }
    public String getAlertType() { return alertType; }
    public String getSeverity() { return severity; }
    public String getStatus() { return status; }
    public Instant getOccurredAt() { return occurredAt; }
    public int getAttemptCount() { return attemptCount; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
}
