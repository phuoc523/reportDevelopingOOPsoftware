package com.scentstation.service;

import com.scentstation.event.MachineAlertChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class AlertHandoffClient {
    private static final Logger logger = LoggerFactory.getLogger(AlertHandoffClient.class);
    private final RestClient restClient;
    private final String notificationUrl;
    private final String maintenanceUrl;
    private final String serviceKey;
    private final AlertHandoffOutboxCleanupService outboxCleanup;
    private final MachineAlertKafkaPublisher kafkaPublisher;

    public AlertHandoffClient(@Value("${machine.integration.notification-service-url:}") String notificationUrl,
                              @Value("${machine.integration.maintenance-service-url:}") String maintenanceUrl,
                              @Value("${machine.integration.shared-secret:}") String serviceKey,
                              AlertHandoffOutboxCleanupService outboxCleanup,
                              MachineAlertKafkaPublisher kafkaPublisher) {
        this.notificationUrl = notificationUrl;
        this.maintenanceUrl = maintenanceUrl;
        this.serviceKey = serviceKey;
        this.outboxCleanup = outboxCleanup;
        this.kafkaPublisher = kafkaPublisher;
        this.restClient = RestClient.builder().build();
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handoff(MachineAlertChangedEvent event) {
        if (kafkaPublisher.isEnabled()) {
            try {
                kafkaPublisher.publish(event);
                outboxCleanup.removeDelivered(event.getEventId(), "notification");
            } catch (RuntimeException exception) {
                logger.warn("Kafka handoff for alert {} failed; persistent outbox will retry", event.getAlertId());
            }
        } else if (!notificationUrl.isBlank()) {
            post(notificationUrl, event, "notification", "notification service");
        }
        boolean needsMaintenance = "CRITICAL".equals(event.getSeverity())
            || "ESCALATED".equals(event.getStatus());
        if (needsMaintenance && !maintenanceUrl.isBlank()) {
            post(maintenanceUrl, event, "maintenance", "maintenance service");
        }
    }

    private void post(String url, MachineAlertChangedEvent event, String targetKey, String target) {
        RestClientException lastFailure = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                restClient.post().uri(url).header("X-Internal-Service-Key", serviceKey)
                    .body(event).retrieve().toBodilessEntity();
                outboxCleanup.removeDelivered(event.getEventId(), targetKey);
                return;
            } catch (RestClientException exception) {
                lastFailure = exception;
                logger.warn("Alert {} handoff to {} failed on attempt {}/3", event.getAlertId(), target, attempt);
            }
        }
        logger.error("Could not hand off alert {} to {}; persistent outbox will retry at {}", event.getAlertId(), target, url, lastFailure);
    }
}
