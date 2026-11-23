package com.scentstation.simulator;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;

public record SimulatorConfig(
        URI brokerUri,
        String machineId,
        String username,
        String password,
        String firmwareVersion,
        long heartbeatIntervalMs,
        long telemetryIntervalMs,
        long reconnectIntervalMs,
        double durationScale,
        Path stateFile,
        FaultMode initialMode,
        List<SlotConfig> slots) {

    public static SimulatorConfig load(ObjectMapper mapper) throws IOException {
        String machineId = required("SIMULATOR_MACHINE_ID");
        requireCanonicalId(machineId, "SIMULATOR_MACHINE_ID");
        URI broker = URI.create(required("SIMULATOR_BROKER_URI"));
        if (!"tcp".equalsIgnoreCase(broker.getScheme()) && !"ssl".equalsIgnoreCase(broker.getScheme())) {
            throw new IllegalArgumentException("SIMULATOR_BROKER_URI must use tcp:// or ssl://");
        }
        if (broker.getHost() == null) throw new IllegalArgumentException("SIMULATOR_BROKER_URI must include a host");

        Path slotFile = Path.of(value("SIMULATOR_SLOT_CONFIG", "config/demo-9101.json"));
        SlotFile file = mapper.readValue(slotFile.toFile(), SlotFile.class);
        if (file.slots() == null || file.slots().isEmpty()) {
            throw new IllegalArgumentException("Simulator slot configuration must contain at least one slot");
        }
        file.slots().forEach(SlotConfig::validate);
        if (file.slots().stream().map(SlotConfig::slotId).distinct().count() != file.slots().size()) {
            throw new IllegalArgumentException("Simulator slot IDs must be unique");
        }

        return new SimulatorConfig(
                broker,
                machineId,
                required("SIMULATOR_MQTT_USERNAME"),
                required("SIMULATOR_MQTT_PASSWORD"),
                value("SIMULATOR_FIRMWARE_VERSION", file.firmwareVersion() == null
                        ? "simulator-1.0.0" : file.firmwareVersion()),
                positiveLong("SIMULATOR_HEARTBEAT_INTERVAL_MS", 5_000),
                positiveLong("SIMULATOR_TELEMETRY_INTERVAL_MS", 5_000),
                positiveLong("SIMULATOR_RECONNECT_INTERVAL_MS", 5_000),
                nonNegativeDouble("SIMULATOR_DURATION_SCALE", 1.0),
                Path.of(value("SIMULATOR_STATE_FILE", "data/device-state.json")),
                FaultMode.valueOf(value("SIMULATOR_MODE", "NORMAL").toUpperCase()),
                List.copyOf(file.slots()));
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }

    private static String value(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static long positiveLong(String name, long fallback) {
        long parsed = Long.parseLong(value(name, Long.toString(fallback)));
        if (parsed <= 0) throw new IllegalArgumentException(name + " must be positive");
        return parsed;
    }

    private static double nonNegativeDouble(String name, double fallback) {
        double parsed = Double.parseDouble(value(name, Double.toString(fallback)));
        if (!Double.isFinite(parsed) || parsed < 0) throw new IllegalArgumentException(name + " must be non-negative");
        return parsed;
    }

    private static void requireCanonicalId(String value, String name) {
        try {
            long parsed = Long.parseLong(value);
            if (parsed <= 0 || !Long.toString(parsed).equals(value)) throw new NumberFormatException();
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(name + " must be a canonical positive decimal ID");
        }
    }

    public record SlotFile(String firmwareVersion, List<SlotConfig> slots) {}

    public record SlotConfig(
            String slotId,
            String productId,
            double doseMl,
            double remainingMl,
            double emptyBottleWeightGrams,
            double densityGramsPerMl,
            String sensorStatus) {
        void validate() {
            requireCanonicalId(slotId, "slotId");
            requireCanonicalId(productId, "productId");
            if (!Double.isFinite(doseMl) || doseMl <= 0
                    || !Double.isFinite(remainingMl) || remainingMl < 0
                    || !Double.isFinite(emptyBottleWeightGrams) || emptyBottleWeightGrams < 0
                    || !Double.isFinite(densityGramsPerMl) || densityGramsPerMl <= 0
                    || sensorStatus == null || sensorStatus.isBlank()) {
                throw new IllegalArgumentException("Invalid simulator configuration for slot " + slotId);
            }
        }
    }

    public enum FaultMode { NORMAL, DOOR_OPEN, LEAK, SENSOR_ERROR, MAINTENANCE }
}
