package com.scentstation.service;

import com.scentstation.entity.DeviceRegistrationStatus;
import com.scentstation.entity.MachineAlertSeverity;
import com.scentstation.entity.MachineAlertSource;
import com.scentstation.entity.MachineAlertType;
import com.scentstation.entity.MachineOperationalStatus;
import com.scentstation.repository.MachineRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(prefix = "machine.mqtt", name = "enabled", havingValue = "true")
public class MachineConnectivityMonitor {
    private final MachineRepository machines;
    private final MachineAlertService alerts;
    private final Duration heartbeatTimeout;
    private final Clock clock;

    @Autowired
    public MachineConnectivityMonitor(MachineRepository machines, MachineAlertService alerts,
            @Value("${machine.mqtt.heartbeat-timeout-ms:15000}") long heartbeatTimeoutMs) {
        this(machines, alerts, heartbeatTimeoutMs, Clock.systemUTC());
    }

    MachineConnectivityMonitor(MachineRepository machines, MachineAlertService alerts,
                               long heartbeatTimeoutMs, Clock clock) {
        if (heartbeatTimeoutMs <= 0) {
            throw new IllegalArgumentException("heartbeat timeout must be positive");
        }
        this.machines = machines;
        this.alerts = alerts;
        this.heartbeatTimeout = Duration.ofMillis(heartbeatTimeoutMs);
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${machine.mqtt.connectivity-check-interval-ms:5000}")
    @Transactional
    public void markTimedOutMachinesOffline() {
        Instant now = clock.instant();
        var stale = machines
            .findTop100ByDeviceRegistrationStatusAndOperationalStatusInAndLastSeenAtLessThanEqualOrderByLastSeenAtAsc(
                DeviceRegistrationStatus.ACTIVE,
                List.of(MachineOperationalStatus.ONLINE, MachineOperationalStatus.UNSTABLE),
                now.minus(heartbeatTimeout));
        for (var machine : stale) {
            machine.setOperationalStatus(MachineOperationalStatus.OFFLINE);
            machines.save(machine);
            alerts.record(machine, MachineAlertType.MACHINE_OFFLINE, MachineAlertSeverity.HIGH,
                MachineAlertSource.MONITOR, null, "Machine heartbeat timed out", now);
        }
    }
}
