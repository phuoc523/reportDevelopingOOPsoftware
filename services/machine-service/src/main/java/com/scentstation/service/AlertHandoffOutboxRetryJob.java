package com.scentstation.service;

import com.scentstation.entity.AlertHandoffOutbox;
import com.scentstation.event.MachineAlertChangedEvent;
import com.scentstation.repository.AlertHandoffOutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
public class AlertHandoffOutboxRetryJob {
    private static final Logger logger = LoggerFactory.getLogger(AlertHandoffOutboxRetryJob.class);
    private final AlertHandoffOutboxRepository outbox;
    private final RestClient restClient = RestClient.builder().build();
    private final String notificationUrl;
    private final String maintenanceUrl;
    private final String serviceKey;

    public AlertHandoffOutboxRetryJob(AlertHandoffOutboxRepository outbox,
                                      @Value("${machine.integration.notification-service-url:}") String notificationUrl,
                                      @Value("${machine.integration.maintenance-service-url:}") String maintenanceUrl,
                                      @Value("${machine.integration.shared-secret:}") String serviceKey) {
        this.outbox = outbox;
        this.notificationUrl = notificationUrl;
        this.maintenanceUrl = maintenanceUrl;
        this.serviceKey = serviceKey;
    }

    @Scheduled(fixedDelayString = "${machine.integration.handoff-retry-interval-ms:60000}", initialDelayString = "${machine.integration.handoff-retry-interval-ms:60000}")
    @Transactional
    public void retryPending() {
        for (AlertHandoffOutbox item : outbox.findTop100ByNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(Instant.now())) {
            String url = "notification".equals(item.getTargetService()) ? notificationUrl : maintenanceUrl;
            if (url == null || url.isBlank()) {
                continue;
            }
            MachineAlertChangedEvent event = new MachineAlertChangedEvent(item.getEventId(), item.getAlertId(), item.getTenantId(),
                item.getMachineId(), item.getLocationId(), item.getAlertType(), item.getSeverity(), item.getStatus(),
                item.getOccurredAt());
            try {
                restClient.post().uri(url).header("X-Internal-Service-Key", serviceKey)
                    .body(event).retrieve().toBodilessEntity();
                outbox.delete(item);
            } catch (RestClientException exception) {
                item.attempted(Instant.now().plus(Math.min(3600L, 30L * (1L << Math.min(item.getAttemptCount(), 7))), ChronoUnit.SECONDS));
                logger.warn("Retrying alert {} handoff to {} failed (attempt {})", item.getAlertId(), item.getTargetService(), item.getAttemptCount());
            }
        }
    }
}
