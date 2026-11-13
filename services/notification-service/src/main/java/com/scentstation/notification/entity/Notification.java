package com.scentstation.notification.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(
    name = "notifications",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_notifications_tenant_id", columnNames = {"tenant_id", "id"}),
        @UniqueConstraint(name = "uk_notifications_source_event", columnNames = {"source", "source_event_id"})
    },
    indexes = {
        @Index(name = "idx_notifications_tenant_created", columnList = "tenant_id, created_at, id")
    }
)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, length = 100)
    private String tenantId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 4000)
    private String content;

    @Column(name = "notification_type", nullable = false, length = 50)
    private String notificationType;

    @Column(nullable = false, length = 20)
    private String priority;

    @Column(nullable = false, length = 50)
    private String source;

    @Column(name = "source_event_id", length = 100)
    private String sourceEventId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "notification", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<NotificationRecipient> recipients = new ArrayList<>();

    protected Notification() {
    }

    public Notification(
            String tenantId,
            String title,
            String content,
            String notificationType,
            String priority,
            String source,
            String sourceEventId
    ) {
        this.tenantId = tenantId;
        this.title = title;
        this.content = content;
        this.notificationType = notificationType;
        this.priority = priority;
        this.source = source;
        this.sourceEventId = sourceEventId;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void addRecipient(String recipientId) {
        recipients.add(new NotificationRecipient(this, recipientId));
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public String getNotificationType() {
        return notificationType;
    }

    public String getPriority() {
        return priority;
    }

    public String getSource() {
        return source;
    }

    public String getSourceEventId() {
        return sourceEventId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<NotificationRecipient> getRecipients() {
        return Collections.unmodifiableList(recipients);
    }
}
