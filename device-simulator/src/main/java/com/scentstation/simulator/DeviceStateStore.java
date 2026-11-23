package com.scentstation.simulator;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Durable command journal: an IN_PROGRESS command is never actuated a second time after restart. */
public final class DeviceStateStore {
    private final ObjectMapper mapper;
    private final Path file;
    private final Map<String, StoredCommand> commands = new LinkedHashMap<>();
    private final Map<String, SlotSnapshot> slots = new LinkedHashMap<>();

    public DeviceStateStore(ObjectMapper mapper, Path file) throws IOException {
        this.mapper = mapper;
        this.file = file.toAbsolutePath().normalize();
        if (Files.exists(this.file)) {
            Snapshot snapshot = mapper.readValue(this.file.toFile(), Snapshot.class);
            if (snapshot.commands() != null) commands.putAll(snapshot.commands());
            if (snapshot.slots() != null) slots.putAll(snapshot.slots());
        }
    }

    public synchronized StoredCommand command(String commandId) { return commands.get(commandId); }

    public synchronized List<StoredCommand> inProgress() {
        return commands.values().stream().filter(command -> "IN_PROGRESS".equals(command.status())).toList();
    }

    public synchronized List<StoredCommand> unacknowledgedResponses() {
        return commands.values().stream().filter(command -> command.responseJson() != null
                && !command.brokerAcknowledged()).toList();
    }

    public synchronized void begin(String commandId, String slotId, String requestFingerprint) throws IOException {
        commands.putIfAbsent(commandId, new StoredCommand(commandId, slotId, requestFingerprint,
                "IN_PROGRESS", null, false));
        persist();
    }

    public synchronized void complete(String commandId, String status, String responseJson) throws IOException {
        StoredCommand old = commands.get(commandId);
        if (old == null) throw new IllegalStateException("Command was not journaled before processing");
        if (old.responseJson() != null) return;
        commands.put(commandId, new StoredCommand(commandId, old.slotId(), old.requestFingerprint(),
                status, responseJson, false));
        persist();
    }

    public synchronized void brokerAcknowledged(String commandId) throws IOException {
        StoredCommand old = commands.get(commandId);
        if (old == null || old.responseJson() == null || old.brokerAcknowledged()) return;
        commands.put(commandId, new StoredCommand(commandId, old.slotId(), old.requestFingerprint(),
                old.status(), old.responseJson(), true));
        persist();
    }

    public synchronized Map<String, SlotSnapshot> initializeSlots(Map<String, SlotSnapshot> configured) throws IOException {
        if (slots.isEmpty()) {
            slots.putAll(configured);
            persist();
        }
        return Map.copyOf(slots);
    }

    public synchronized void updateSlot(String slotId, SlotSnapshot snapshot) throws IOException {
        slots.put(slotId, snapshot);
        persist();
    }

    public synchronized SlotSnapshot slot(String slotId) { return slots.get(slotId); }

    private void persist() throws IOException {
        Path parent = file.getParent();
        if (parent != null) Files.createDirectories(parent);
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        mapper.writeValue(temporary.toFile(), new Snapshot(new LinkedHashMap<>(commands), new LinkedHashMap<>(slots)));
        try {
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public record StoredCommand(String commandId, String slotId, String requestFingerprint, String status,
                                String responseJson, boolean brokerAcknowledged) {}
    public record SlotSnapshot(double remainingMl, double weightGrams, String sensorStatus) {}
    private record Snapshot(Map<String, StoredCommand> commands, Map<String, SlotSnapshot> slots) {}
}
