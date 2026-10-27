package com.scentstation.service;

import com.scentstation.entity.MachineDeviceEvent;
import com.scentstation.entity.MachineTelemetry;
import com.scentstation.repository.MachineDeviceEventRepository;
import com.scentstation.repository.MachineTelemetryRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Component
public class DeviceHistoryRetentionJob {
    private static final int BATCH_SIZE = 500;
    private static final int MAX_BATCHES_PER_RUN = 10;

    private final MachineTelemetryRepository telemetry;
    private final MachineDeviceEventRepository events;
    private final Duration retention;

    public DeviceHistoryRetentionJob(MachineTelemetryRepository telemetry,
                                     MachineDeviceEventRepository events,
                                     @Value("${machine.history.retention-days:90}") long retentionDays) {
        if (retentionDays <= 0) {
            throw new IllegalArgumentException("machine.history.retention-days must be positive");
        }
        this.telemetry = telemetry;
        this.events = events;
        this.retention = Duration.ofDays(retentionDays);
    }

    @Scheduled(cron = "${machine.history.retention-cron:0 20 3 * * *}")
    public void purgeExpiredHistory() {
        Instant cutoff = Instant.now().minus(retention);
        for (int batch = 0; batch < MAX_BATCHES_PER_RUN; batch++) {
            List<MachineTelemetry> expired = telemetry.findTop500ByOccurredAtBeforeOrderByOccurredAtAsc(cutoff);
            if (expired.isEmpty()) {
                break;
            }
            telemetry.deleteAllInBatch(expired);
            if (expired.size() < BATCH_SIZE) {
                break;
            }
        }
        for (int batch = 0; batch < MAX_BATCHES_PER_RUN; batch++) {
            List<MachineDeviceEvent> expired = events.findTop500ByOccurredAtBeforeOrderByOccurredAtAsc(cutoff);
            if (expired.isEmpty()) {
                break;
            }
            events.deleteAllInBatch(expired);
            if (expired.size() < BATCH_SIZE) {
                break;
            }
        }
    }
}
