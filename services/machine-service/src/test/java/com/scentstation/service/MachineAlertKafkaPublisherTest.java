package com.scentstation.service;

import com.scentstation.event.MachineAlertChangedEvent;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class MachineAlertKafkaPublisherTest {
    @Test
    void waitsForBrokerAcknowledgement() {
        KafkaTemplate<String, MachineAlertChangedEvent> kafka = mock(KafkaTemplate.class);
        when(kafka.send(eq("machine-alert.v1"), eq("7"), any()))
            .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));
        MachineAlertKafkaPublisher publisher = new MachineAlertKafkaPublisher(kafka, true, "machine-alert.v1");
        MachineAlertChangedEvent event = event();

        publisher.publish(event);

        verify(kafka).send("machine-alert.v1", "7", event);
    }

    @Test
    void reportsFailedBrokerAcknowledgement() {
        KafkaTemplate<String, MachineAlertChangedEvent> kafka = mock(KafkaTemplate.class);
        CompletableFuture<SendResult<String, MachineAlertChangedEvent>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new IllegalStateException("broker unavailable"));
        when(kafka.send(eq("machine-alert.v1"), eq("7"), any())).thenReturn(failed);
        MachineAlertKafkaPublisher publisher = new MachineAlertKafkaPublisher(kafka, true, "machine-alert.v1");

        assertThrows(IllegalStateException.class, () -> publisher.publish(event()));
    }

    private MachineAlertChangedEvent event() {
        return new MachineAlertChangedEvent("event-1", 1L, "7", 2L, "3",
            "MACHINE_OFFLINE", "CRITICAL", "OPEN", Instant.parse("2026-11-15T00:00:00Z"));
    }
}
