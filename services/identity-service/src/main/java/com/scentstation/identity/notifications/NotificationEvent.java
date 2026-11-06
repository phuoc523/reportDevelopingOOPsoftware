package com.scentstation.identity.notifications;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="notification_events",uniqueConstraints=@UniqueConstraint(name="uk_notification_event_tenant",columnNames={"tenant_id","event_id"}))
public class NotificationEvent {
    @Id @Column(length=36) private String id=UUID.randomUUID().toString();
    @Column(name="tenant_id",nullable=false,length=64) private String tenantId;
    @Column(name="event_id",nullable=false,length=80) private String eventId;
    @Column(name="event_type",nullable=false,length=64) private String eventType;
    @Column(name="created_at",nullable=false) private Instant createdAt;
    protected NotificationEvent(){}
    public NotificationEvent(String tenantId,String eventId,String eventType,Instant createdAt){
        this.tenantId=tenantId;this.eventId=eventId;this.eventType=eventType;this.createdAt=createdAt;
    }
    public String getId(){return id;}
    public String getTenantId(){return tenantId;}
    public String getEventId(){return eventId;}
}
