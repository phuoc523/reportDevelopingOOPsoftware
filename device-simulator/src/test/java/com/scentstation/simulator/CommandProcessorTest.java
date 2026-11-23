package com.scentstation.simulator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.scentstation.simulator.DeviceStateStore.SlotSnapshot;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CommandProcessorTest {
    @TempDir Path temporaryDirectory;

    @Test
    void validCommandDispensesOneDoseAndDuplicateReplaysWithoutSecondDose() throws Exception {
        Fixture fixture = new Fixture(10.0, "NORMAL");
        String command = fixture.command("cmd-001", "91001");

        fixture.processor.onCommand(command);
        assertEquals(9.88, fixture.state.slot("91001").remainingMl(), 0.0001);
        assertEquals(List.of("ACKNOWLEDGED", "DISPENSED"), fixture.responseStatuses());

        String originalResult = fixture.messages.get(1).json();
        fixture.processor.onCommand(command);

        assertEquals(9.88, fixture.state.slot("91001").remainingMl(), 0.0001);
        assertEquals(originalResult, fixture.messages.get(2).json());

        String mismatchedDuplicate = command.replace("\"slotId\":\"91001\"", "\"slotId\":\"91002\"");
        fixture.processor.onCommand(mismatchedDuplicate);
        assertEquals(3, fixture.messages.size(), "A commandId reused with different parameters must be ignored");
    }

    @Test
    void emptySlotAndSafetyFaultReturnFailureWithoutActuation() throws Exception {
        Fixture empty = new Fixture(0, "NORMAL");
        empty.processor.onCommand(empty.command("cmd-empty", "91001"));
        assertEquals(0, empty.state.slot("91001").remainingMl(), 0.0001);
        assertEquals("FAILED", empty.responseStatuses().getFirst());
        assertTrue(empty.messages.stream().anyMatch(message -> message.topic().endsWith("/alert")));

        Fixture unsafe = new Fixture(10, "NORMAL");
        unsafe.processor.setMode(SimulatorConfig.FaultMode.LEAK);
        unsafe.processor.onCommand(unsafe.command("cmd-leak", "91001"));
        assertEquals(10, unsafe.state.slot("91001").remainingMl(), 0.0001);
        assertTrue(unsafe.responseStatuses().contains("FAILED"));
        assertTrue(unsafe.messages.stream().anyMatch(message -> message.json().contains("LEAK_DETECTED")));

        Fixture sensor = new Fixture(10, "ERROR");
        sensor.processor.onCommand(sensor.command("cmd-sensor", "91001"));
        assertEquals(10, sensor.state.slot("91001").remainingMl(), 0.0001);
        assertEquals("FAILED", sensor.responseStatuses().getFirst());
        assertTrue(sensor.messages.stream().anyMatch(message -> message.json().contains("SENSOR_ERROR")));

        for (var mode : List.of(SimulatorConfig.FaultMode.DOOR_OPEN, SimulatorConfig.FaultMode.MAINTENANCE)) {
            Fixture fault = new Fixture(10, "NORMAL");
            fault.processor.setMode(mode);
            fault.processor.onCommand(fault.command("cmd-" + mode, "91001"));
            assertEquals(10, fault.state.slot("91001").remainingMl(), 0.0001);
            assertEquals("FAILED", fault.responseStatuses().getLast());
            assertTrue(fault.messages.stream().anyMatch(message -> message.json().contains(
                    mode == SimulatorConfig.FaultMode.DOOR_OPEN ? "DOOR_OPEN" : "MAINTENANCE_REQUIRED")));
        }
    }

    @Test
    void unfinishedCommandAfterRestartBecomesUnknownAndIsNotReexecuted() throws Exception {
        Fixture fixture = new Fixture(10, "NORMAL");
        String command = fixture.command("cmd-restart", "91001");
        String expiry = fixture.mapper.readTree(command).path("payload").path("expiresAt").asText();
        fixture.store.begin("cmd-restart", "91001", "cmd-restart|91001|DISPENSE|5|" + Instant.parse(expiry));
        DeviceStateStore restartedStore = new DeviceStateStore(fixture.mapper, fixture.config.stateFile());
        SimulatorDeviceState restartedState = new SimulatorDeviceState(fixture.config.slots(), restartedStore,
                SimulatorConfig.FaultMode.NORMAL);
        CommandProcessor restartedProcessor = new CommandProcessor(fixture.mapper, fixture.config, restartedState,
                restartedStore, (topic, json) -> {
                    fixture.messages.add(new Published(topic, json));
                    return CompletableFuture.completedFuture(null);
                });

        restartedProcessor.recoverInterruptedCommands();
        restartedProcessor.onCommand(command);

        assertEquals(10, restartedState.slot("91001").remainingMl(), 0.0001);
        assertEquals("UNKNOWN", fixture.responseStatuses().getFirst());
        assertEquals(2, fixture.messages.size());
        assertEquals(fixture.messages.get(0).json(), fixture.messages.get(1).json());
    }

    @Test
    void rejectsWrongMachineAndExpiredCommandWithoutSuccess() throws Exception {
        Fixture fixture = new Fixture(10, "NORMAL");
        String wrongMachine = fixture.command("cmd-wrong-machine", "91001")
                .replace("\"machineId\":\"9101\"", "\"machineId\":\"9102\"");
        fixture.processor.onCommand(wrongMachine);
        assertTrue(fixture.messages.isEmpty());

        fixture.processor.onCommand(fixture.command("cmd-expired", "91001", Instant.now().minusSeconds(1)));
        assertEquals("EXPIRED", fixture.responseStatuses().getFirst());
        assertFalse(fixture.responseStatuses().contains("DISPENSED"));
    }

    private final class Fixture {
        private final com.fasterxml.jackson.databind.ObjectMapper mapper = MqttDeviceSimulator.newObjectMapper();
        private final List<Published> messages = new ArrayList<>();
        private final SimulatorConfig config = new SimulatorConfig(
                java.net.URI.create("tcp://localhost:1883"), "9101", "device-test", "not-used",
                "test-simulator", 5_000, 5_000, 5_000, 0,
                temporaryDirectory.resolve("state-" + UUID.randomUUID() + ".json"),
                SimulatorConfig.FaultMode.NORMAL,
                List.of(new SimulatorConfig.SlotConfig("91001", "91001", 0.12, 10,
                        18, 0.85, "NORMAL")));
        private final DeviceStateStore store = new DeviceStateStore(mapper, config.stateFile());
        private final SimulatorDeviceState state;
        private final CommandProcessor processor;

        private Fixture(double quantity, String sensorStatus) throws Exception {
            store.initializeSlots(Map.of("91001", new SlotSnapshot(quantity, 18 + quantity * 0.85, sensorStatus)));
            state = new SimulatorDeviceState(config.slots(), store, SimulatorConfig.FaultMode.NORMAL);
            if (!"NORMAL".equals(sensorStatus)) state.slot("91001").sensorStatus(sensorStatus);
            processor = new CommandProcessor(mapper, config, state, store, (topic, json) -> {
                messages.add(new Published(topic, json));
                return CompletableFuture.completedFuture(null);
            });
        }

        private String command(String commandId, String slotId) throws Exception {
            return command(commandId, slotId, Instant.now().plusSeconds(30));
        }

        private String command(String commandId, String slotId, Instant expiresAt) throws Exception {
            return mapper.writeValueAsString(Map.of("schemaVersion", 1, "messageId", UUID.randomUUID().toString(),
                    "machineId", "9101", "type", "DISPENSE_COMMAND", "timestamp", Instant.now().toString(),
                    "payload", Map.of("commandId", commandId, "action", "DISPENSE", "slotId", slotId,
                            "durationMs", 5, "expiresAt", expiresAt.toString())));
        }

        private List<String> responseStatuses() throws Exception {
            return messages.stream().filter(message -> message.topic().endsWith("/response"))
                    .map(message -> {
                        try { JsonNode tree = mapper.readTree(message.json()); return tree.path("payload").path("status").asText(); }
                        catch (Exception exception) { throw new IllegalStateException(exception); }
                    }).toList();
        }
    }

    private record Published(String topic, String json) {}
}
