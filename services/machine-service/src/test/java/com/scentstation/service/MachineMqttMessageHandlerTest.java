package com.scentstation.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.scentstation.config.MachineMqttProperties;
import com.scentstation.entity.DeviceRegistrationStatus;
import com.scentstation.entity.Machine;
import com.scentstation.entity.MachineCommand;
import com.scentstation.entity.MachineOperationalStatus;
import com.scentstation.repository.MachineCommandRepository;
import com.scentstation.repository.MachineRepository;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MachineMqttMessageHandlerTest {
    private static final Instant NOW = Instant.parse("2026-11-24T08:00:00Z");
    private final MachineRepository machines = mock(MachineRepository.class);
    private final MachineCommandRepository commands = mock(MachineCommandRepository.class);
    private final MachineAlertService alerts = mock(MachineAlertService.class);
    private final MachineMqttProperties properties = new MachineMqttProperties();
    private final MachineMqttMessageHandler handler = new MachineMqttMessageHandler(
        new ObjectMapper().registerModule(new JavaTimeModule()), machines, commands, alerts,
        properties, new MqttMessageIdTracker(), Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void appliesVerifiedDispenseResultAndRestoresConnectivity() {
        Machine machine = machine(MachineOperationalStatus.OFFLINE);
        MachineCommand command = mock(MachineCommand.class);
        when(commands.findByCommandIdAndMachineIdForUpdate("cmd-1", 9101L))
            .thenReturn(Optional.of(command));

        handler.handle("scentstation/9101/response", json("""
            {"schemaVersion":1,"messageId":"550e8400-e29b-41d4-a716-446655440000",
             "machineId":"9101","type":"COMMAND_RESPONSE","timestamp":"2026-11-24T08:00:00Z",
             "payload":{"commandId":"cmd-1","status":"DISPENSED","details":"LED cycle completed"}}
            """));

        verify(command).complete(true, "LED cycle completed", NOW);
        verify(machine).setLastSeenAt(NOW);
        verify(machine).setOperationalStatus(MachineOperationalStatus.ONLINE);
        verify(alerts).resolveMachineOfflineAlerts(machine, NOW);
        verify(commands).save(command);
    }

    @Test
    void acceptsHeartbeatAndRejectsDuplicateMessageId() {
        Machine machine = machine(MachineOperationalStatus.ONLINE);
        byte[] heartbeat = json("""
            {"schemaVersion":1,"messageId":"550e8400-e29b-41d4-a716-446655440001",
             "machineId":"9101","type":"HEARTBEAT","timestamp":"2026-11-24T08:00:00Z",
             "payload":{"status":"ONLINE","uptimeSeconds":10,"firmwareVersion":"esp32-led-1.0.0"}}
            """);

        handler.handle("scentstation/9101/heartbeat", heartbeat);

        verify(machine).setLastSeenAt(NOW);
        assertThatThrownBy(() -> handler.handle("scentstation/9101/heartbeat", heartbeat))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Duplicate");
    }

    @Test
    void rejectsEnvelopeForAnotherMachine() {
        assertThatThrownBy(() -> handler.handle("scentstation/9101/heartbeat", json("""
            {"schemaVersion":1,"messageId":"550e8400-e29b-41d4-a716-446655440002",
             "machineId":"9102","type":"HEARTBEAT","timestamp":"2026-11-24T08:00:00Z",
             "payload":{"status":"ONLINE"}}
            """)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("does not match");
    }

    private Machine machine(MachineOperationalStatus status) {
        Machine machine = mock(Machine.class);
        when(machine.getId()).thenReturn(9101L);
        when(machine.getDeviceRegistrationStatus()).thenReturn(DeviceRegistrationStatus.ACTIVE);
        when(machine.getOperationalStatus()).thenReturn(status);
        when(machines.findByIdForAlertUpdate(9101L)).thenReturn(Optional.of(machine));
        return machine;
    }

    private byte[] json(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
