package com.scentstation.identity.audit;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "identity_audit_events", indexes = {
    @Index(name = "idx_identity_audit_tenant_time", columnList = "tenant_id,occurred_at")
})
public class AuditEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt = Instant.now();
    private Long actorId;
    @Column(name = "tenant_id")
    private Long tenantId;
    @Column(nullable = false, length = 80)
    private String action;
    @Column(nullable = false, length = 80)
    private String targetType;
    @Column(length = 160)
    private String targetId;
    @Column(nullable = false, length = 20)
    private String outcome;
    @Column(length = 1000)
    private String beforeState;
    @Column(length = 1000)
    private String afterState;

    protected AuditEvent() {}
    public AuditEvent(Long actorId, Long tenantId, String action, String targetType, String targetId,
                      String outcome, String beforeState, String afterState) {
        this.actorId = actorId;
        this.tenantId = tenantId;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.outcome = outcome;
        this.beforeState = beforeState;
        this.afterState = afterState;
    }
    public Long getId() { return id; }
    public Instant getOccurredAt() { return occurredAt; }
    public Long getActorId() { return actorId; }
    public Long getTenantId() { return tenantId; }
    public String getAction() { return action; }
    public String getTargetType() { return targetType; }
    public String getTargetId() { return targetId; }
    public String getOutcome() { return outcome; }
    public String getBeforeState() { return beforeState; }
    public String getAfterState() { return afterState; }
}
