package com.scentstation.identity.audit;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** Append-only application API. DB migration should revoke UPDATE/DELETE privileges in production. */
@Entity
@Table(name="platform_audit_events",indexes={
    @Index(name="idx_audit_tenant_time",columnList="tenant_id,occurred_at"),
    @Index(name="idx_audit_actor_time",columnList="actor_id,occurred_at")})
public class AuditEvent {
    @Id @Column(length=36) private String id=UUID.randomUUID().toString();
    @Column(name="tenant_id",length=64) private String tenantId;
    @Column(name="actor_id",nullable=false,length=64) private String actorId;
    @Column(name="action_name",nullable=false,length=80) private String action;
    @Column(name="resource_type",nullable=false,length=64) private String resourceType;
    @Column(name="resource_id",nullable=false,length=128) private String resourceId;
    @Column(name="outcome",nullable=false,length=16) private String outcome;
    @Column(name="request_id",nullable=false,length=64) private String requestId;
    @Column(name="occurred_at",nullable=false) private Instant occurredAt;
    protected AuditEvent(){}
    public AuditEvent(String tenantId,String actorId,String action,String resourceType,String resourceId,String outcome,String requestId,Instant when){
        this.tenantId=tenantId;this.actorId=actorId;this.action=action;this.resourceType=resourceType;
        this.resourceId=resourceId;this.outcome=outcome;this.requestId=requestId;this.occurredAt=when;
    }
    public String getId(){return id;}
    public String getTenantId(){return tenantId;}
    public String getActorId(){return actorId;}
    public String getAction(){return action;}
    public String getResourceType(){return resourceType;}
    public String getResourceId(){return resourceId;}
    public String getOutcome(){return outcome;}
    public String getRequestId(){return requestId;}
    public Instant getOccurredAt(){return occurredAt;}
}
