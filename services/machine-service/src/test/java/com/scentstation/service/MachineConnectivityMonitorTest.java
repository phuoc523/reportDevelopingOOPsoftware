package com.scentstation.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.scentstation.entity.DeviceRegistrationStatus;
import com.scentstation.entity.Machine;
import com.scentstation.entity.MachineAlertSeverity;
import com.scentstation.entity.MachineAlertSource;
import com.scentstation.entity.MachineAlertType;
import com.scentstation.entity.MachineOperationalStatus;
import com.scentstation.repository.MachineRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class MachineConnectivityMonitorTest {
    @Test
    void marksStaleOnlineMachineOfflineAndRaisesAlert() {
        Instant now = Instant.parse("2026-11-24T08:00:00Z");
        MachineRepository machines = mock(MachineRepository.class);
        MachineAlertService alerts = mock(MachineAlertService.class);
        Machine machine = mock(Machine.class);
        when(machines
            .findTop100ByDeviceRegistrationStatusAndOperationalStatusInAndLastSeenAtLessThanEqualOrderByLastSeenAtAsc(
                DeviceRegistrationStatus.ACTIVE,
                List.of(MachineOperationalStatus.ONLINE, MachineOperationalStatus.UNSTABLE),
                now.minusSeconds(15))).thenReturn(List.of(machine));

        new MachineConnectivityMonitor(machines, alerts, 15_000,
            Clock.fixed(now, ZoneOffset.UTC)).markTimedOutMachinesOffline();

        verify(machine).setOperationalStatus(MachineOperationalStatus.OFFLINE);
        verify(machines).save(machine);
        verify(alerts).record(machine, MachineAlertType.MACHINE_OFFLINE, MachineAlertSeverity.HIGH,
            MachineAlertSource.MONITOR, null, "Machine heartbeat timed out", now);
    }
}
