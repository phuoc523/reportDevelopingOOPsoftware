package com.scentstation.notification.event;

import com.scentstation.notification.repository.NotificationRepository;
import com.scentstation.notification.security.IdentityAuthorizationClient;
import com.scentstation.notification.security.NotificationAuthorizationUnavailableException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
    "notification.kafka.enabled=true",
    "notification.kafka.machine-alert-topic=machine-alert.test.v1",
    "notification.kafka.group-id=notification-test-v1",
    "notification.kafka.retry-attempts=2",
    "notification.kafka.retry-backoff-ms=10",
    "notification.kafka.dead-letter-topic=machine-alert.test.v1.DLT"
})
@EmbeddedKafka(
    partitions = 1,
    topics = {"machine-alert.test.v1", "machine-alert.test.v1.DLT"},
    bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
@ActiveProfiles("test")
@DirtiesContext
@Import(MachineAlertKafkaIntegrationTests.ProbeConfiguration.class)
class MachineAlertKafkaIntegrationTests {
    @Autowired KafkaTemplate<String, MachineAlertEvent> kafka;
    @Autowired NotificationRepository notifications;
    @Autowired DeadLetterProbe deadLetters;
    @MockBean IdentityAuthorizationClient identity;

    @BeforeEach
    void resetState() {
        notifications.deleteAll();
        deadLetters.events.clear();
        reset(identity);
    }

    @Test
    void consumesAndTreatsRedeliveryAsIdempotent() throws Exception {
        when(identity.resolveNotificationRecipients("7")).thenReturn(List.of("42"));
        MachineAlertEvent event = event("event-kafka-1", 1);

        kafka.send("machine-alert.test.v1", "7", event).get();
        kafka.send("machine-alert.test.v1", "7", event).get();

        await(() -> notifications.count() == 1);
        verify(identity, times(1)).resolveNotificationRecipients("7");
    }

    @Test
    void sendsInvalidPayloadDirectlyToDeadLetterTopic() throws Exception {
        kafka.send("machine-alert.test.v1", "7", event("event-invalid", 2)).get();

        MachineAlertEvent failed = deadLetters.events.poll(10, TimeUnit.SECONDS);
        assertNotNull(failed);
        assertEquals("event-invalid", failed.eventId());
        verifyNoInteractions(identity);
    }

    @Test
    void retriesTransientFailureThenSendsToDeadLetterTopic() throws Exception {
        when(identity.resolveNotificationRecipients("7"))
            .thenThrow(new NotificationAuthorizationUnavailableException());
        kafka.send("machine-alert.test.v1", "7", event("event-retry", 1)).get();

        MachineAlertEvent failed = deadLetters.events.poll(10, TimeUnit.SECONDS);
        assertNotNull(failed);
        assertEquals("event-retry", failed.eventId());
        verify(identity, atLeast(2)).resolveNotificationRecipients("7");
    }

    private MachineAlertEvent event(String eventId, int version) {
        return new MachineAlertEvent(version, eventId, 11L, "7", 22L, "3",
            "MACHINE_OFFLINE", "CRITICAL", "OPEN", Instant.parse("2026-11-15T00:00:00Z"),
            "machine-service");
    }

    private void await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(25);
        }
        assertTrue(condition.getAsBoolean(), "Condition was not met before timeout");
    }

    @TestConfiguration
    static class ProbeConfiguration {
        @Bean DeadLetterProbe deadLetterProbe() {
            return new DeadLetterProbe();
        }
    }

    static class DeadLetterProbe {
        final BlockingQueue<MachineAlertEvent> events = new LinkedBlockingQueue<>();

        @KafkaListener(topics = "machine-alert.test.v1.DLT", groupId = "machine-alert-dlt-test")
        void receive(MachineAlertEvent event) {
            events.add(event);
        }
    }
}
