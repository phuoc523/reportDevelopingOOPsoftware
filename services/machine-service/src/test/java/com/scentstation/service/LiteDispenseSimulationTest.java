package com.scentstation.service;

import com.scentstation.entity.MachineCommand;
import com.scentstation.entity.MachineCommandStatus;
import com.scentstation.repository.MachineCommandRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LiteDispenseSimulationTest {
    private final MachineCommandRepository commands = mock(MachineCommandRepository.class);
    private final DispenseCommandService service = new DispenseCommandService(commands, null, null, null, 60_000, true);

    @Test
    void acknowledgesBeforeDelayAndCompletesTheSameCommandOnce() {
        MachineCommand command = acknowledgedAt(Instant.now());
        ReflectionTestUtils.setField(service, "simulationDelayMs", 60_000L);
        when(commands.findByCommandIdForUpdate("command")).thenReturn(Optional.of(command));

        service.get("command", "tenant");
        assertEquals(MachineCommandStatus.ACKNOWLEDGED, command.getStatus());

        ReflectionTestUtils.setField(service, "simulationDelayMs", 1L);
        command = acknowledgedAt(Instant.now().minusSeconds(2));
        when(commands.findByCommandIdForUpdate("command")).thenReturn(Optional.of(command));
        service.get("command", "tenant");
        Instant completedAt = command.getCompletedAt();
        service.get("command", "tenant");
        assertEquals(MachineCommandStatus.DISPENSED, command.getStatus());
        assertEquals(completedAt, command.getCompletedAt());
    }

    private MachineCommand acknowledgedAt(Instant issuedAt) {
        MachineCommand command = new MachineCommand("command", "tenant", "order", "payment",
            1L, "slot", "fingerprint", issuedAt, issuedAt.plusSeconds(120));
        command.acknowledge(issuedAt);
        return command;
    }
}
