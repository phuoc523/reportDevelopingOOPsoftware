package com.scentstation.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(
    name = "notification_recipients",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_notification_recipient", columnNames = {"notification_id", "recipient_id"})
    },
    indexes = {
        @Index(
            name = "idx_notification_recipient_inbox",
            columnList = "tenant_id, recipient_id, read_at, created_at, id"
        )
    }
)
public class NotificationRecipient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumns({
        @JoinColumn(name = "tenant_id", referencedColumnName = "tenant_id", nullable = false),
        @JoinColumn(name = "notification_id", referencedColumnName = "id", nullable = false)
    })
    private Notification notification;

    @Column(name = "recipient_id", nullable = false, length = 100)
    private String recipientId;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected NotificationRecipient() {
    }

    NotificationRecipient(Notification notification, String recipientId) {
        this.notification = notification;
        this.recipientId = recipientId;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void markAsRead() {
        if (readAt == null) {
            readAt = Instant.now();
            updatedAt = readAt;
        }
    }

    public void markAsUnread() {
        if (readAt != null) {
            readAt = null;
            updatedAt = Instant.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Notification getNotification() {
        return notification;
    }

    public String getRecipientId() {
        return recipientId;
    }

    public Instant getReadAt() {
        return readAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
