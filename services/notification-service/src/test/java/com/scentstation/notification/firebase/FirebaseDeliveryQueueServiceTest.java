package com.scentstation.notification.firebase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.scentstation.notification.entity.FirebaseDeliveryJob;
import com.scentstation.notification.repository.FirebaseDeliveryJobRepository;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;

class FirebaseDeliveryQueueServiceTest {
    @Test
    void notificationCreatedEventBecomesPendingFirebaseJob() {
        FirebaseDeliveryJobRepository jobs = mock(FirebaseDeliveryJobRepository.class);
        FirebaseDeliveryQueueService queue = new FirebaseDeliveryQueueService(
            jobs, mock(FirebaseNotificationDeliveryService.class),
            mock(PlatformTransactionManager.class), 20, 5, Duration.ofSeconds(10),
            Duration.ofMinutes(5));
        NotificationCreatedEvent event = new NotificationCreatedEvent(
            12L, "tenant-a", "Nước hoa sắp hết", "Slot 1 còn 5%", "MACHINE_ALERT",
            "MEDIUM", "machine-service", "event-low-stock", List.of("user-1"));

        queue.enqueue(event);

        ArgumentCaptor<FirebaseDeliveryJob> saved = ArgumentCaptor.forClass(FirebaseDeliveryJob.class);
        verify(jobs).save(saved.capture());
        assertThat(saved.getValue().getNotificationId()).isEqualTo(12L);
        assertThat(saved.getValue().getStatus()).isEqualTo(FirebaseDeliveryJob.Status.PENDING);
        assertThat(saved.getValue().event().recipientIds()).containsExactly("user-1");
    }
}
