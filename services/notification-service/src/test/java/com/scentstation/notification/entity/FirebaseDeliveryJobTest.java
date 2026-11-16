package com.scentstation.notification.entity;

import com.scentstation.notification.firebase.NotificationCreatedEvent;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FirebaseDeliveryJobTest {
    private final Instant createdAt = Instant.parse("2026-11-16T00:00:00Z");

    @Test
    void preservesDeliveryPayloadAndStartsPending() {
        FirebaseDeliveryJob job = new FirebaseDeliveryJob(event(), createdAt);
        assertThat(job.getStatus()).isEqualTo(FirebaseDeliveryJob.Status.PENDING);
        assertThat(job.getAttempts()).isZero();
        assertThat(job.getNextAttemptAt()).isEqualTo(createdAt);
        assertThat(job.event()).isEqualTo(event());
    }

    @Test
    void retriesWithLeaseRecoveryThenStopsAtAttemptLimit() {
        FirebaseDeliveryJob job = new FirebaseDeliveryJob(event(), createdAt);
        job.claim(createdAt.plusSeconds(1), createdAt.plusSeconds(301));
        job.retryOrFail("FcmDeliveryException", List.of("token-2"),
            createdAt.plusSeconds(2), createdAt.plusSeconds(12), 2);
        assertThat(job.getStatus()).isEqualTo(FirebaseDeliveryJob.Status.RETRYING);
        assertThat(job.getAttempts()).isOne();
        assertThat(job.getNextAttemptAt()).isEqualTo(createdAt.plusSeconds(12));
        assertThat(job.getRetryTokens()).containsExactly("token-2");

        job.claim(createdAt.plusSeconds(12), createdAt.plusSeconds(312));
        job.retryOrFail("FcmDeliveryException", List.of("token-2"),
            createdAt.plusSeconds(13), createdAt.plusSeconds(33), 2);
        assertThat(job.getStatus()).isEqualTo(FirebaseDeliveryJob.Status.FAILED);
        assertThat(job.getAttempts()).isEqualTo(2);
        assertThat(job.getLastErrorType()).isEqualTo("FcmDeliveryException");
    }

    @Test
    void recordsSuccessfulCompletion() {
        FirebaseDeliveryJob job = new FirebaseDeliveryJob(event(), createdAt);
        job.claim(createdAt.plusSeconds(1), createdAt.plusSeconds(301));
        job.complete(FirebaseDeliveryJob.Status.SENT, 2, 2, 0, createdAt.plusSeconds(2));
        assertThat(job.getStatus()).isEqualTo(FirebaseDeliveryJob.Status.SENT);
        assertThat(job.getLastErrorType()).isNull();
        assertThat(job.getRetryTokens()).isEmpty();
    }

    private NotificationCreatedEvent event() {
        return new NotificationCreatedEvent(12L, "tenant-a", "Máy ngoại tuyến", "Kiểm tra kiosk",
            "MACHINE_ALERT", "CRITICAL", "machine-service", "event-1", List.of("user-1"));
    }
}
