package com.scentstation.simulator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scentstation.simulator.DeviceStateStore.StoredCommand;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Implements MQTT v1 command validation, durable dedupe, simulated actuation and response emission. */
public final class CommandProcessor {
    private static final Logger log = LoggerFactory.getLogger(CommandProcessor.class);
    private final ObjectMapper mapper;
    private final SimulatorConfig config;
    private final SimulatorDeviceState state;
    private final DeviceStateStore store;
    private final Supplier<Instant> clock;
    private final MessagePublisher publisher;

    public CommandProcessor(ObjectMapper mapper, SimulatorConfig config, SimulatorDeviceState state,
                            DeviceStateStore store, MessagePublisher publisher) {
        this(mapper, config, state, store, publisher, Instant::now);
    }

    CommandProcessor(ObjectMapper mapper, SimulatorConfig config, SimulatorDeviceState state,
                     DeviceStateStore store, MessagePublisher publisher, Supplier<Instant> clock) {
        this.mapper = mapper;
        this.config = config;
        this.state = state;
        this.store = store;
        this.publisher = publisher;
        this.clock = clock;
    }

    public synchronized void onCommand(String raw) {
        final Command command;
        try {
            command = parse(raw);
        } catch (RuntimeException exception) {
            log.warn("Ignoring invalid MQTT command: {}", exception.getMessage());
            return;
        }

        StoredCommand existing = store.command(command.commandId());
        if (existing != null) {
            if (existing.requestFingerprint() == null || !existing.requestFingerprint().equals(command.fingerprint())) {
                log.error("Ignoring commandId {} reused with different command parameters", command.commandId());
                return;
            }
            if (existing.responseJson() != null) {
                publishStored(existing);
            } else {
                finish(command.commandId(), command.slotId(), "UNKNOWN",
                        "Device restarted while command execution was in progress; actuation was not repeated");
            }
            return;
        }

        if (command.expiresAt().isBefore(clock.get())) {
            storeAndPublish(command, "EXPIRED", "Command expired before device execution");
            return;
        }
        if (!"DISPENSE".equals(command.action())) {
            storeAndPublish(command, "FAILED", "Unsupported device action");
            return;
        }

        SimulatorDeviceState.SlotState slot = state.slot(command.slotId());
        if (slot == null) {
            storeAndPublish(command, "FAILED", "Command references an unknown device slot");
            publishAlert("SENSOR_ERROR", "HIGH", null, "Command references an unknown simulator slot");
            return;
        }
        String unsafeReason = unsafeReason(slot);
        if (unsafeReason != null) {
            storeAndPublish(command, "FAILED", unsafeReason);
            publishSafetyAlert(command.slotId(), slot);
            return;
        }
        if (!slot.hasEnoughStock()) {
            storeAndPublish(command, "FAILED", "Simulator slot is out of perfume");
            publishAlert("OUT_OF_STOCK", "HIGH", slot.id(), "Simulator slot is empty");
            return;
        }

        try {
            store.begin(command.commandId(), command.slotId(), command.fingerprint());
            publishResponse(command.commandId(), response(command.commandId(), "ACKNOWLEDGED", "Command accepted by simulator"));
            long delay = Math.min(60_000, Math.round(command.durationMs() * config.durationScale()));
            if (delay > 0) TimeUnit.MILLISECONDS.sleep(delay);

            unsafeReason = unsafeReason(slot);
            if (unsafeReason != null) {
                finish(command.commandId(), command.slotId(), "UNKNOWN",
                        "Safety condition changed during dispensing; physical outcome cannot be confirmed: " + unsafeReason);
                publishSafetyAlert(command.slotId(), slot);
                return;
            }
            if (!state.dispenseIfSafe(slot)) {
                finish(command.commandId(), command.slotId(), "UNKNOWN",
                        "Slot stock or sensor state changed during actuation; physical outcome is uncertain");
                return;
            }
            finish(command.commandId(), command.slotId(), "DISPENSED", "Virtual spray completed");
            if (slot.remainingMl() <= slot.doseMl()) {
                publishAlert(slot.remainingMl() == 0 ? "OUT_OF_STOCK" : "LOW_STOCK",
                        slot.remainingMl() == 0 ? "HIGH" : "MEDIUM", slot.id(),
                        slot.remainingMl() == 0 ? "Simulator slot is empty" : "Simulator slot is nearly empty");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            finish(command.commandId(), command.slotId(), "UNKNOWN",
                    "Simulator was interrupted during actuation; physical outcome cannot be confirmed");
        } catch (IOException exception) {
            log.error("Could not persist command state {}; refusing to retry actuation", command.commandId(), exception);
        }
    }

    public synchronized void recoverInterruptedCommands() {
        for (StoredCommand command : store.inProgress()) {
            finish(command.commandId(), command.slotId(), "UNKNOWN",
                    "Simulator restarted during actuation; command will not be executed again");
        }
    }

    public synchronized void retryUnacknowledgedResponses() {
        for (StoredCommand command : store.unacknowledgedResponses()) publishStored(command);
    }

    public String telemetry() throws IOException {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("uptimeSeconds", Math.max(0, TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startedAtNanos)));
        payload.put("doorOpen", state.doorOpen());
        payload.put("leakDetected", state.leakDetected());
        payload.put("sensorStatus", state.sensorStatus());
        payload.put("slots", state.slots().stream().map(slot -> Map.of(
                "slotId", slot.id(), "weightGrams", slot.weightGrams(), "sensorStatus", slot.sensorStatus())).toList());
        return envelope("TELEMETRY", payload);
    }

    private final long startedAtNanos = System.nanoTime();

    public String heartbeat() throws IOException {
        return envelope("HEARTBEAT", Map.of("status", "ONLINE",
                "uptimeSeconds", Math.max(0, TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startedAtNanos)),
                "firmwareVersion", config.firmwareVersion()));
    }

    public String rebootEvent() throws IOException {
        return envelope("DEVICE_EVENT", Map.of("eventType", "DEVICE_REBOOTED", "details", "Virtual device started"));
    }

    public void setMode(SimulatorConfig.FaultMode mode) {
        state.mode(mode);
        if (mode != SimulatorConfig.FaultMode.NORMAL) publishCurrentFaultAlert(null);
    }

    public void publishCurrentFaultAlert(String slotId) {
        switch (state.mode()) {
            case DOOR_OPEN -> publishAlert("DOOR_OPEN", "HIGH", slotId, "Simulator reports that the machine door is open");
            case LEAK -> publishAlert("LEAK_DETECTED", "CRITICAL", slotId, "Simulator reports a leak");
            case SENSOR_ERROR -> publishAlert("SENSOR_ERROR", "HIGH", slotId, "Simulator sensor is reporting an error");
            case MAINTENANCE -> publishAlert("MAINTENANCE_REQUIRED", "HIGH", slotId, "Simulator requires maintenance");
            case NORMAL -> { }
        }
    }

    private void publishSafetyAlert(String slotId, SimulatorDeviceState.SlotState slot) {
        if (state.mode() == SimulatorConfig.FaultMode.NORMAL && slot != null
                && !"NORMAL".equalsIgnoreCase(slot.sensorStatus())) {
            publishAlert("SENSOR_ERROR", "HIGH", slotId, "Simulator slot sensor is reporting an error");
        } else {
            publishCurrentFaultAlert(slotId);
        }
    }

    private String unsafeReason(SimulatorDeviceState.SlotState slot) {
        return switch (state.mode()) {
            case DOOR_OPEN -> "Machine door is open";
            case LEAK -> "Leak detected";
            case SENSOR_ERROR -> "Machine sensor error";
            case MAINTENANCE -> "Machine requires maintenance";
            case NORMAL -> !"NORMAL".equalsIgnoreCase(slot.sensorStatus()) ? "Slot sensor error" : null;
        };
    }

    private void storeAndPublish(Command command, String status, String details) {
        try {
            store.begin(command.commandId(), command.slotId(), command.fingerprint());
            finish(command.commandId(), command.slotId(), status, details);
        } catch (IOException exception) {
            log.error("Could not persist rejected command {}", command.commandId(), exception);
        }
    }

    private void finish(String commandId, String slotId, String status, String details) {
        try {
            String json = response(commandId, status, details);
            store.complete(commandId, status, json);
            publishResponse(commandId, json);
        } catch (IOException exception) {
            log.error("Could not persist response for command {}", commandId, exception);
        }
    }

    private void publishStored(StoredCommand command) {
        if (command.responseJson() != null) publishResponse(command.commandId(), command.responseJson());
    }

    private void publishResponse(String commandId, String json) {
        publisher.publish("scentstation/" + config.machineId() + "/response", json)
                .whenComplete((ignored, error) -> {
                    if (error != null) {
                        log.warn("Broker did not acknowledge response for command {}", commandId);
                        return;
                    }
                    try { store.brokerAcknowledged(commandId); }
                    catch (IOException exception) { log.error("Could not persist broker receipt for {}", commandId, exception); }
                });
    }

    private void publishAlert(String type, String severity, String slotId, String summary) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("alertType", type);
            payload.put("severity", severity);
            if (slotId != null) payload.put("slotId", slotId);
            payload.put("summary", summary);
            publisher.publish("scentstation/" + config.machineId() + "/alert", envelope("DEVICE_ALERT", payload));
        } catch (IOException exception) {
            log.error("Could not serialize simulator alert {}", type, exception);
        }
    }

    private String response(String commandId, String status, String details) {
        try { return envelope("COMMAND_RESPONSE", Map.of("commandId", commandId, "status", status, "details", details)); }
        catch (IOException exception) { throw new IllegalStateException("Could not serialize command response", exception); }
    }

    private String envelope(String type, Object payload) throws IOException {
        return mapper.writeValueAsString(Map.of("schemaVersion", 1, "messageId", UUID.randomUUID().toString(),
                "machineId", config.machineId(), "type", type, "timestamp", clock.get().truncatedTo(ChronoUnit.MICROS),
                "payload", payload));
    }

    private Command parse(String raw) {
        try {
            JsonNode envelope = mapper.readTree(raw);
            if (envelope == null || envelope.path("schemaVersion").asInt(-1) != 1
                    || !config.machineId().equals(envelope.path("machineId").asText())
                    || !isUuid(envelope.path("messageId").asText())
                    || !"DISPENSE_COMMAND".equals(envelope.path("type").asText())
                    || !envelope.path("payload").isObject()) throw new IllegalArgumentException("invalid v1 command envelope");
            JsonNode payload = envelope.path("payload");
            String id = payload.path("commandId").asText();
            String slot = payload.path("slotId").asText();
            String action = payload.path("action").asText();
            long duration = payload.path("durationMs").asLong(-1);
            Instant issuedAt = Instant.parse(envelope.path("timestamp").asText());
            Instant expiresAt = Instant.parse(payload.path("expiresAt").asText());
            if (id.isBlank() || id.length() > 100) throw new IllegalArgumentException("commandId is invalid");
            canonicalPositiveId(slot, "slotId");
            if (duration <= 0 || duration > 120_000) throw new IllegalArgumentException("durationMs is outside the accepted range");
            Instant now = clock.get();
            if (issuedAt.isBefore(now.minusSeconds(300)) || issuedAt.isAfter(now.plusSeconds(30))) {
                throw new IllegalArgumentException("command timestamp is outside the accepted age/clock-skew window");
            }
            return new Command(id, slot, action, duration, expiresAt);
        } catch (IOException | java.time.DateTimeException exception) {
            throw new IllegalArgumentException("invalid v1 command payload", exception);
        }
    }

    private void canonicalPositiveId(String value, String field) {
        try {
            long parsed = Long.parseLong(value);
            if (parsed <= 0 || !Long.toString(parsed).equals(value)) throw new NumberFormatException();
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(field + " must be a canonical positive decimal identifier");
        }
    }

    private boolean isUuid(String value) {
        try { return UUID.fromString(value).toString().equalsIgnoreCase(value); }
        catch (RuntimeException exception) { return false; }
    }

    private record Command(String commandId, String slotId, String action, long durationMs, Instant expiresAt) {
        private String fingerprint() {
            return commandId + '|' + slotId + '|' + action + '|' + durationMs + '|' + expiresAt;
        }
    }

    @FunctionalInterface
    public interface MessagePublisher {
        CompletableFuture<Void> publish(String topic, String json);
    }
}
