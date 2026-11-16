package com.scentstation.notification.entity;

import com.scentstation.notification.firebase.NotificationCreatedEvent;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "firebase_delivery_jobs", uniqueConstraints =
    @UniqueConstraint(name = "uk_firebase_delivery_notification", columnNames = "notification_id"))
public class FirebaseDeliveryJob {
    public enum Status { PENDING, PROCESSING, RETRYING, SENT, PARTIAL, SKIPPED, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "notification_id", nullable = false)
    private Long notificationId;

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

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "firebase_delivery_recipients", joinColumns = @JoinColumn(name = "job_id"))
    @Column(name = "recipient_id", nullable = false, length = 100)
    private List<String> recipientIds = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "firebase_delivery_retry_tokens", joinColumns = @JoinColumn(name = "job_id"))
    @Column(name = "firebase_token", nullable = false, length = 512)
    private List<String> retryTokens = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "last_error_type", length = 200)
    private String lastErrorType;

    @Column(name = "requested_count", nullable = false)
    private int requestedCount;

    @Column(name = "success_count", nullable = false)
    private int successCount;

    @Column(name = "failure_count", nullable = false)
    private int failureCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected FirebaseDeliveryJob() {}

    public FirebaseDeliveryJob(NotificationCreatedEvent event, Instant now) {
        notificationId = event.notificationId();
        tenantId = event.tenantId();
        title = event.title();
        content = event.content();
        notificationType = event.notificationType();
        priority = event.priority();
        source = event.source();
        sourceEventId = event.sourceEventId();
        recipientIds.addAll(event.recipientIds());
        status = Status.PENDING;
        nextAttemptAt = now;
        createdAt = now;
        updatedAt = now;
    }

    public void claim(Instant now, Instant leaseUntil) {
        status = Status.PROCESSING;
        attempts++;
        nextAttemptAt = leaseUntil;
        updatedAt = now;
    }

    public void complete(Status outcome, int requested, int succeeded, int failed, Instant now) {
        status = outcome;
        requestedCount = requested;
        successCount = succeeded;
        failureCount = failed;
        lastErrorType = null;
        retryTokens.clear();
        nextAttemptAt = now;
        updatedAt = now;
    }

    public void retryOrFail(String errorType, List<String> tokens, Instant now, Instant retryAt, int maxAttempts) {
        lastErrorType = errorType == null ? "Unknown" : errorType.substring(0, Math.min(errorType.length(), 200));
        retryTokens.clear();
        if (tokens != null) retryTokens.addAll(tokens);
        status = attempts >= maxAttempts ? Status.FAILED : Status.RETRYING;
        nextAttemptAt = attempts >= maxAttempts ? now : retryAt;
        updatedAt = now;
    }

    public NotificationCreatedEvent event() {
        return new NotificationCreatedEvent(notificationId, tenantId, title, content, notificationType,
            priority, source, sourceEventId, recipientIds);
    }

    public Long getId() { return id; }
    public Long getNotificationId() { return notificationId; }
    public Status getStatus() { return status; }
    public int getAttempts() { return attempts; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public String getLastErrorType() { return lastErrorType; }
    public List<String> getRetryTokens() { return List.copyOf(retryTokens); }
}
