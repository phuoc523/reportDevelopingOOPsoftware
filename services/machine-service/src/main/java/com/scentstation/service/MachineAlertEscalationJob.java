package com.scentstation.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
public class MachineAlertEscalationJob {
    private final MachineAlertService alerts;
    private final Duration escalationDelay;

    public MachineAlertEscalationJob(MachineAlertService alerts,
                                     @Value("${machine.alerts.escalate-after-ms:300000}") long escalationDelayMs) {
        this.alerts = alerts;
        this.escalationDelay = Duration.ofMillis(escalationDelayMs);
    }

    @Scheduled(fixedDelayString = "${machine.alerts.escalation-interval-ms:60000}")
    public void escalateOverdueAlerts() {
        alerts.escalateOverdueAlerts(escalationDelay, Instant.now());
    }
}
