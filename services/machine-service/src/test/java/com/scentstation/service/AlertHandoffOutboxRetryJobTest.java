package com.scentstation.service;

import com.scentstation.entity.AlertHandoffOutbox;
import com.scentstation.repository.AlertHandoffOutboxRepository;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AlertHandoffOutboxRetryJobTest {
    @Test
    void retriesSuccessfulDeliveryAndRemovesOutboxRecord() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/handoff", exchange -> exchange.sendResponseHeaders(204, -1));
        server.start();
        try {
            AlertHandoffOutboxRepository repository = mock(AlertHandoffOutboxRepository.class);
            AlertHandoffOutbox item = item();
            when(repository.findTop100ByNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(any(Instant.class)))
                .thenReturn(List.of(item));
            AlertHandoffOutboxRetryJob job = new AlertHandoffOutboxRetryJob(repository,
                "http://127.0.0.1:" + server.getAddress().getPort() + "/handoff", "", "test-key",
                disabledKafkaPublisher());

            job.retryPending();

            verify(repository).delete(item);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void failedDeliveryKeepsRecordAndSchedulesLaterAttempt() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/handoff", exchange -> exchange.sendResponseHeaders(503, -1));
        server.start();
        try {
            AlertHandoffOutboxRepository repository = mock(AlertHandoffOutboxRepository.class);
            AlertHandoffOutbox item = item();
            when(repository.findTop100ByNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(any(Instant.class)))
                .thenReturn(List.of(item));
            AlertHandoffOutboxRetryJob job = new AlertHandoffOutboxRetryJob(repository,
                "http://127.0.0.1:" + server.getAddress().getPort() + "/handoff", "", "test-key",
                disabledKafkaPublisher());

            job.retryPending();

            assertEquals(1, item.getAttemptCount());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void removesOutboxOnlyAfterKafkaAcknowledges() {
        AlertHandoffOutboxRepository repository = mock(AlertHandoffOutboxRepository.class);
        MachineAlertKafkaPublisher publisher = mock(MachineAlertKafkaPublisher.class);
        AlertHandoffOutbox item = item();
        when(repository.findTop100ByNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(any()))
            .thenReturn(List.of(item));
        when(publisher.isEnabled()).thenReturn(true);
        AlertHandoffOutboxRetryJob job = new AlertHandoffOutboxRetryJob(
            repository, "", "", "", publisher);

        job.retryPending();

        verify(publisher).publish(any());
        verify(repository).delete(item);
    }

    @Test
    void keepsOutboxWhenKafkaDoesNotAcknowledge() {
        AlertHandoffOutboxRepository repository = mock(AlertHandoffOutboxRepository.class);
        MachineAlertKafkaPublisher publisher = mock(MachineAlertKafkaPublisher.class);
        AlertHandoffOutbox item = item();
        when(repository.findTop100ByNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(any()))
            .thenReturn(List.of(item));
        when(publisher.isEnabled()).thenReturn(true);
        doThrow(new IllegalStateException("broker unavailable")).when(publisher).publish(any());
        AlertHandoffOutboxRetryJob job = new AlertHandoffOutboxRetryJob(
            repository, "", "", "", publisher);

        job.retryPending();

        assertEquals(1, item.getAttemptCount());
        verify(repository, never()).delete(item);
    }

    private AlertHandoffOutbox item() {
        return new AlertHandoffOutbox("notification", "event-1", 1L, "tenant-1", 2L,
            "location-1", "LOW_STOCK", "MEDIUM", "ESCALATED", Instant.now());
    }

    private MachineAlertKafkaPublisher disabledKafkaPublisher() {
        MachineAlertKafkaPublisher publisher = mock(MachineAlertKafkaPublisher.class);
        when(publisher.isEnabled()).thenReturn(false);
        return publisher;
    }
}
