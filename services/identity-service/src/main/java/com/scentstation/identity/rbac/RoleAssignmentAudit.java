package com.scentstation.identity.rbac;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "role_assignment_audit", indexes = {
        @Index(name = "idx_role_audit_tenant_time", columnList = "target_tenant_id,occurred_at")
})
public class RoleAssignmentAudit {
    @Id
    @Column(length = 36, nullable = false)
    private String id = UUID.randomUUID().toString();

    @Column(name = "actor_user_id", length = 36, nullable = false)
    private String actorUserId;

    @Column(name = "target_user_id", length = 36, nullable = false)
    private String targetUserId;

    @Column(name = "target_tenant_id", length = 36)
    private String targetTenantId;

    @Column(nullable = false, length = 12)
    private String action;

    @Column(name = "role_name", nullable = false, length = 64)
    private String roleName;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected RoleAssignmentAudit() {}

    public RoleAssignmentAudit(String actorUserId, String targetUserId, String targetTenantId,
                               String action, Role role, Instant occurredAt) {
        this.actorUserId = actorUserId;
        this.targetUserId = targetUserId;
        this.targetTenantId = targetTenantId;
        this.action = action;
        this.roleName = role.name();
        this.occurredAt = occurredAt;
    }

    public String getId() { return id; }
    public String getActorUserId() { return actorUserId; }
    public String getTargetUserId() { return targetUserId; }
    public String getTargetTenantId() { return targetTenantId; }
    public String getAction() { return action; }
    public String getRoleName() { return roleName; }
    public Instant getOccurredAt() { return occurredAt; }
}
