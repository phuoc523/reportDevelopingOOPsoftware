package com.scentstation.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.scentstation.config.MachineMqttProperties;
import com.scentstation.entity.DeviceRegistrationStatus;
import com.scentstation.entity.Machine;
import com.scentstation.entity.MachineCommand;
import com.scentstation.entity.MachineAlertSeverity;
import com.scentstation.entity.MachineAlertSource;
import com.scentstation.entity.MachineAlertType;
import com.scentstation.entity.MachineOperationalStatus;
import com.scentstation.entity.MachineSlot;
import com.scentstation.entity.MachineTelemetryLatest;
import com.scentstation.repository.MachineCommandRepository;
import com.scentstation.repository.MachineRepository;
import com.scentstation.repository.MachineSlotRepository;
import com.scentstation.repository.MachineTelemetryLatestRepository;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MachineMqttMessageHandlerTest {
    private static final Instant NOW = Instant.parse("2026-11-24T08:00:00Z");
    private final MachineRepository machines = mock(MachineRepository.class);
    private final MachineCommandRepository commands = mock(MachineCommandRepository.class);
    private final MachineSlotRepository slots = mock(MachineSlotRepository.class);
    private final MachineTelemetryLatestRepository telemetry = mock(MachineTelemetryLatestRepository.class);
    private final MachineAlertService alerts = mock(MachineAlertService.class);
    private final MachineMqttProperties properties = new MachineMqttProperties();
    private final MachineMqttMessageHandler handler = new MachineMqttMessageHandler(
        new ObjectMapper().registerModule(new JavaTimeModule()), machines, commands, slots, telemetry, alerts,
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

    @Test
    void storesTelemetryAndRaisesDeduplicatedLowStockAlert() {
        Machine machine = machine(MachineOperationalStatus.ONLINE);
        MachineSlot slot = slot(91001L, 10);
        when(slots.findByIdAndMachineId(91001L, 9101L)).thenReturn(Optional.of(slot));
        when(telemetry.findByMachineIdAndSlotId(9101L, 91001L)).thenReturn(Optional.empty());

        handler.handle("scentstation/9101/telemetry", json("""
            {"schemaVersion":1,"messageId":"550e8400-e29b-41d4-a716-446655440003",
             "machineId":"9101","type":"TELEMETRY","timestamp":"2026-11-24T08:00:00Z",
             "payload":{"slots":[{"slotId":"91001","remainingPercent":5.25,"sensorStatus":"NORMAL"}]}}
            """));

        ArgumentCaptor<MachineTelemetryLatest> saved = ArgumentCaptor.forClass(MachineTelemetryLatest.class);
        verify(telemetry).save(saved.capture());
        assertThat(saved.getValue().getRemainingPercent()).isEqualByComparingTo("5.25");
        verify(alerts).record(machine, MachineAlertType.LOW_STOCK, MachineAlertSeverity.MEDIUM,
            MachineAlertSource.DEVICE, 91001L,
            "Perfume level 5.25% is at or below 10%", NOW);
    }

    @Test
    void newerHealthyTelemetryResolvesDeviceLowStockAlert() {
        Machine machine = machine(MachineOperationalStatus.ONLINE);
        MachineSlot slot = slot(91001L, 10);
        MachineTelemetryLatest existing = new MachineTelemetryLatest(9101L, 91001L,
            BigDecimal.valueOf(5), "NORMAL", NOW.minusSeconds(60), NOW.minusSeconds(60));
        when(slots.findByIdAndMachineId(91001L, 9101L)).thenReturn(Optional.of(slot));
        when(telemetry.findByMachineIdAndSlotId(9101L, 91001L)).thenReturn(Optional.of(existing));

        handler.handle("scentstation/9101/telemetry", json("""
            {"schemaVersion":1,"messageId":"550e8400-e29b-41d4-a716-446655440004",
             "machineId":"9101","type":"TELEMETRY","timestamp":"2026-11-24T08:00:00Z",
             "payload":{"slots":[{"slotId":"91001","remainingPercent":50,"sensorStatus":"NORMAL"}]}}
            """));

        verify(alerts).resolveObservedAlert(machine, MachineAlertType.LOW_STOCK, 91001L,
            MachineAlertSource.DEVICE, "Perfume level recovered above configured threshold", NOW);
        assertThat(existing.getRemainingPercent()).isEqualByComparingTo("50");
    }

    private MachineSlot slot(Long id, int threshold) {
        MachineSlot slot = mock(MachineSlot.class);
        when(slot.getId()).thenReturn(id);
        when(slot.getStockThreshold()).thenReturn(threshold);
        when(slot.isEnabled()).thenReturn(true);
        when(slot.getProductId()).thenReturn("product-1");
        return slot;
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
