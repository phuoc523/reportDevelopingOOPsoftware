package com.scentstation.service;

import com.scentstation.entity.DeviceRegistrationStatus;
import com.scentstation.entity.Machine;
import com.scentstation.entity.MachineAlertSeverity;
import com.scentstation.entity.MachineAlertSource;
import com.scentstation.entity.MachineAlertType;
import com.scentstation.entity.MachineOperationalStatus;
import com.scentstation.repository.MachineRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.lang.NonNull;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Component
public class MachineHealthMonitor {
    private static final List<MachineOperationalStatus> STALE_STATUSES =
        List.of(MachineOperationalStatus.ONLINE, MachineOperationalStatus.UNSTABLE);
    private static final List<MachineOperationalStatus> RECOVERABLE_STATUSES =
        List.of(MachineOperationalStatus.UNSTABLE, MachineOperationalStatus.OFFLINE);

    private final MachineRepository machines;
    private final MachineAlertService alerts;
    private final TransactionTemplate transactions;
    private final Duration unstableAfter;
    private final Duration offlineAfter;
    private final Duration escalationDelay;

    public MachineHealthMonitor(MachineRepository machines, MachineAlertService alerts,
                                @NonNull PlatformTransactionManager transactionManager,
                                @Value("${machine.monitor.unstable-after-ms:30000}") long unstableAfterMs,
                                @Value("${machine.monitor.offline-after-ms:90000}") long offlineAfterMs,
                                @Value("${machine.alerts.escalate-after-ms:300000}") long escalationAfterMs) {
        if (unstableAfterMs <= 0 || offlineAfterMs <= unstableAfterMs || escalationAfterMs <= 0) {
            throw new IllegalArgumentException("Machine monitoring thresholds must be positive and offline-after must exceed unstable-after");
        }
        this.machines = machines;
        this.alerts = alerts;
        this.transactions = new TransactionTemplate(transactionManager);
        this.unstableAfter = Duration.ofMillis(unstableAfterMs);
        this.offlineAfter = Duration.ofMillis(offlineAfterMs);
        this.escalationDelay = Duration.ofMillis(escalationAfterMs);
    }

    @Scheduled(fixedDelayString = "${machine.monitor.interval-ms:15000}")
    public void refreshMachineStatuses() {
        Instant now = Instant.now();
        Instant unstableCutoff = now.minus(unstableAfter);
        Instant offlineCutoff = now.minus(offlineAfter);

        machines.findTop100ByDeviceRegistrationStatusAndOperationalStatusInAndLastSeenAtLessThanEqualOrderByLastSeenAtAsc(
            DeviceRegistrationStatus.ACTIVE, STALE_STATUSES, offlineCutoff)
            .forEach(machine -> updateMachine(machine.getId(), now));
        machines.findTop100ByDeviceRegistrationStatusAndOperationalStatusAndLastSeenAtAfterAndLastSeenAtBeforeOrderByLastSeenAtAsc(
            DeviceRegistrationStatus.ACTIVE, MachineOperationalStatus.ONLINE,
            offlineCutoff, unstableCutoff)
            .forEach(machine -> updateMachine(machine.getId(), now));
        machines.findTop100ByDeviceRegistrationStatusAndOperationalStatusInAndLastSeenAtAfterOrderByLastSeenAtAsc(
            DeviceRegistrationStatus.ACTIVE, RECOVERABLE_STATUSES, unstableCutoff)
            .forEach(machine -> updateMachine(machine.getId(), now));

        transactions.executeWithoutResult(status -> alerts.escalateOverdueAlerts(escalationDelay, now));
    }

    private void updateMachine(Long machineId, Instant now) {
        transactions.executeWithoutResult(transactionStatus -> {
            Machine machine = machines.findByIdForAlertUpdate(machineId).orElse(null);
            if (machine == null
                    || machine.getDeviceRegistrationStatus() != DeviceRegistrationStatus.ACTIVE
                    || machine.getLastSeenAt() == null) {
                return;
            }

            Duration age = Duration.between(machine.getLastSeenAt(), now);
            MachineOperationalStatus currentStatus = machine.getOperationalStatus();
            if (age.compareTo(offlineAfter) >= 0
                    && (currentStatus == MachineOperationalStatus.ONLINE
                        || currentStatus == MachineOperationalStatus.UNSTABLE)) {
                machine.setOperationalStatus(MachineOperationalStatus.OFFLINE);
                machines.save(machine);
                alerts.record(machine, MachineAlertType.MACHINE_OFFLINE,
                    MachineAlertSeverity.HIGH, MachineAlertSource.MONITOR, null,
                    "Device heartbeat is overdue", now);
            } else if (age.compareTo(unstableAfter) >= 0
                    && age.compareTo(offlineAfter) < 0
                    && currentStatus == MachineOperationalStatus.ONLINE) {
                machine.setOperationalStatus(MachineOperationalStatus.UNSTABLE);
                machines.save(machine);
            } else if (age.compareTo(unstableAfter) < 0
                    && (currentStatus == MachineOperationalStatus.OFFLINE
                        || currentStatus == MachineOperationalStatus.UNSTABLE)) {
                machine.setOperationalStatus(MachineOperationalStatus.ONLINE);
                machines.save(machine);
                alerts.resolveMachineOfflineAlerts(machine, now);
            }
        });
    }
}
