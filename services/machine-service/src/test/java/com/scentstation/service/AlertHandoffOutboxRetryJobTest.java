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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
                "http://127.0.0.1:" + server.getAddress().getPort() + "/handoff", "", "test-key");

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
                "http://127.0.0.1:" + server.getAddress().getPort() + "/handoff", "", "test-key");

            job.retryPending();

            assertEquals(1, item.getAttemptCount());
        } finally {
            server.stop(0);
        }
    }

    private AlertHandoffOutbox item() {
        return new AlertHandoffOutbox("notification", "event-1", 1L, "tenant-1", 2L,
            "location-1", "LOW_STOCK", "MEDIUM", "ESCALATED", Instant.now());
    }
}
