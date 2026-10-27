package com.scentstation.identity.notifications;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="notification_deliveries",indexes=@Index(name="idx_notif_inbox",columnList="tenant_id,user_id,created_at"))
public class NotificationDelivery {
    @Id @Column(length=36) private String id=UUID.randomUUID().toString();
    @Column(name="event_id",nullable=false,length=80) private String eventId;
    @Column(name="tenant_id",nullable=false,length=64) private String tenantId;
    @Column(name="user_id",nullable=false,length=36) private String userId;
    @Column(name="title",nullable=false,length=120) private String title;
    @Column(name="message_text",nullable=false,length=500) private String message;
    @Column(name="delivery_status",nullable=false,length=20) private String status="DELIVERED";
    @Column(name="created_at",nullable=false) private Instant createdAt;
    protected NotificationDelivery(){}
    public NotificationDelivery(String eventId,String tenantId,String userId,String title,String message,Instant createdAt){
        this.eventId=eventId;this.tenantId=tenantId;this.userId=userId;this.title=title;this.message=message;this.createdAt=createdAt;
    }
    public String getId(){return id;}
    public String getEventId(){return eventId;}
    public String getTenantId(){return tenantId;}
    public String getUserId(){return userId;}
    public String getTitle(){return title;}
    public String getMessage(){return message;}
    public String getStatus(){return status;}
    public Instant getCreatedAt(){return createdAt;}
}
