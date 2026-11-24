package com.scentstation.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scentstation.config.MachineMqttProperties;
import com.scentstation.entity.DeviceRegistrationStatus;
import com.scentstation.entity.Machine;
import com.scentstation.entity.MachineCommand;
import com.scentstation.entity.MachineAlertSeverity;
import com.scentstation.entity.MachineAlertSource;
import com.scentstation.entity.MachineAlertType;
import com.scentstation.entity.MachineOperationalStatus;
import com.scentstation.entity.MachineTelemetryLatest;
import com.scentstation.mqtt.MqttEnvelope;
import com.scentstation.mqtt.MqttTopics;
import com.scentstation.repository.MachineCommandRepository;
import com.scentstation.repository.MachineRepository;
import com.scentstation.repository.MachineSlotRepository;
import com.scentstation.repository.MachineTelemetryLatestRepository;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MachineMqttMessageHandler {
    private final ObjectMapper objectMapper;
    private final MachineRepository machines;
    private final MachineCommandRepository commands;
    private final MachineSlotRepository slots;
    private final MachineTelemetryLatestRepository telemetry;
    private final MachineAlertService alerts;
    private final MachineMqttProperties properties;
    private final MqttMessageIdTracker messageIds;
    private final Clock clock;

    @Autowired
    public MachineMqttMessageHandler(ObjectMapper objectMapper, MachineRepository machines,
                                     MachineCommandRepository commands, MachineSlotRepository slots,
                                     MachineTelemetryLatestRepository telemetry,
                                     MachineAlertService alerts,
                                     MachineMqttProperties properties) {
        this(objectMapper, machines, commands, slots, telemetry, alerts, properties,
            new MqttMessageIdTracker(), Clock.systemUTC());
    }

    MachineMqttMessageHandler(ObjectMapper objectMapper, MachineRepository machines,
                              MachineCommandRepository commands, MachineSlotRepository slots,
                              MachineTelemetryLatestRepository telemetry,
                              MachineAlertService alerts,
                              MachineMqttProperties properties, MqttMessageIdTracker messageIds,
                              Clock clock) {
        this.objectMapper = objectMapper;
        this.machines = machines;
        this.commands = commands;
        this.slots = slots;
        this.telemetry = telemetry;
        this.alerts = alerts;
        this.properties = properties;
        this.messageIds = messageIds;
        this.clock = clock;
    }

    @Transactional
    public void handle(String topic, byte[] bytes) {
        if (bytes == null || bytes.length == 0 || bytes.length > properties.getMaxPayloadBytes()) {
            throw new IllegalArgumentException("Invalid MQTT payload size");
        }

        MqttTopics.ParsedTopic parsedTopic = MqttTopics.parse(topic);
        MqttEnvelope envelope = read(bytes);
        Instant now = clock.instant();
        validate(envelope, parsedTopic, now);

        Machine machine = machines.findByIdForAlertUpdate(parsedTopic.machineId())
            .filter(value -> value.getDeviceRegistrationStatus() == DeviceRegistrationStatus.ACTIVE)
            .orElseThrow(() -> new IllegalArgumentException("Machine is unknown or inactive"));
        if (!messageIds.markIfNew(Long.toString(machine.getId()), envelope.messageId(), now)) {
            throw new IllegalArgumentException("Duplicate MQTT messageId");
        }

        touch(machine, now);
        if ("response".equals(parsedTopic.channel())) {
            applyCommandResponse(machine, envelope.payload(), now);
        } else if ("telemetry".equals(parsedTopic.channel())) {
            applyTelemetry(machine, envelope.payload(), envelope.timestamp(), now);
        }
        machines.save(machine);
    }

    private MqttEnvelope read(byte[] bytes) {
        try {
            return objectMapper.readValue(bytes, MqttEnvelope.class);
        } catch (IOException | RuntimeException exception) {
            throw new IllegalArgumentException("Invalid MQTT envelope", exception);
        }
    }

    private void validate(MqttEnvelope envelope, MqttTopics.ParsedTopic topic, Instant now) {
        if (envelope == null || envelope.schemaVersion() != 1
                || !Long.toString(topic.machineId()).equals(envelope.machineId())
                || !MqttTopics.expectedType(topic.channel()).equals(envelope.type())
                || envelope.timestamp() == null || envelope.payload() == null
                || !envelope.payload().isObject()) {
            throw new IllegalArgumentException("MQTT envelope does not match its topic");
        }
        try {
            UUID.fromString(envelope.messageId());
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("MQTT messageId must be a UUID", exception);
        }
        long skew = properties.getAllowedClockSkewSeconds();
        if (envelope.timestamp().isBefore(now.minusSeconds(skew))
                || envelope.timestamp().isAfter(now.plusSeconds(skew))) {
            throw new IllegalArgumentException("MQTT timestamp is outside the allowed clock skew");
        }
    }

    private void touch(Machine machine, Instant now) {
        boolean recovered = machine.getOperationalStatus() == MachineOperationalStatus.OFFLINE;
        machine.setLastSeenAt(now);
        if (machine.getOperationalStatus() == MachineOperationalStatus.OFFLINE
                || machine.getOperationalStatus() == MachineOperationalStatus.UNSTABLE) {
            machine.setOperationalStatus(MachineOperationalStatus.ONLINE);
        }
        if (recovered) {
            alerts.resolveMachineOfflineAlerts(machine, now);
        }
    }

    private void applyCommandResponse(Machine machine, JsonNode payload, Instant now) {
        String commandId = requiredText(payload, "commandId");
        String status = requiredText(payload, "status");
        String details = payload.path("details").asText("");
        if (details.length() > 500) {
            details = details.substring(0, 500);
        }
        MachineCommand command = commands.findByCommandIdAndMachineIdForUpdate(commandId, machine.getId())
            .orElseThrow(() -> new IllegalArgumentException("Unknown command for machine"));
        switch (status) {
            case "ACKNOWLEDGED" -> command.acknowledge(now);
            case "DISPENSED" -> command.complete(true, details, now);
            case "FAILED" -> command.complete(false, details, now);
            case "UNKNOWN" -> command.markUnknown(details, now);
            case "EXPIRED" -> command.markExpired(details, now);
            default -> throw new IllegalArgumentException("Unsupported command response status");
        }
        commands.save(command);
    }

    private void applyTelemetry(Machine machine, JsonNode payload, Instant measuredAt, Instant receivedAt) {
        JsonNode readings = payload.path("slots");
        if (!readings.isArray() || readings.isEmpty() || readings.size() > 32) {
            throw new IllegalArgumentException("telemetry slots must contain between 1 and 32 readings");
        }
        Set<Long> seenSlots = new HashSet<>();
        for (JsonNode reading : readings) {
            Long slotId = requiredLong(reading, "slotId");
            if (!seenSlots.add(slotId)) {
                throw new IllegalArgumentException("telemetry contains a duplicate slotId");
            }
            JsonNode remainingNode = reading.path("remainingPercent");
            if (!remainingNode.isNumber()) {
                throw new IllegalArgumentException("remainingPercent must be a number");
            }
            BigDecimal remaining = remainingNode.decimalValue();
            if (remaining.compareTo(BigDecimal.ZERO) < 0
                    || remaining.compareTo(BigDecimal.valueOf(100)) > 0
                    || remaining.stripTrailingZeros().scale() > 2) {
                throw new IllegalArgumentException("remainingPercent must be between 0 and 100 with at most two decimals");
            }
            String sensorStatus = requiredText(reading, "sensorStatus");
            if (sensorStatus.length() > 30) {
                throw new IllegalArgumentException("sensorStatus is too long");
            }
            var slot = slots.findByIdAndMachineId(slotId, machine.getId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown slot for machine"));
            MachineTelemetryLatest snapshot = telemetry.findByMachineIdAndSlotId(machine.getId(), slotId)
                .orElse(null);
            if (snapshot != null && !measuredAt.isAfter(snapshot.getMeasuredAt())) {
                continue;
            }
            if (snapshot == null) {
                snapshot = new MachineTelemetryLatest(machine.getId(), slotId, remaining,
                    sensorStatus, measuredAt, receivedAt);
            } else {
                snapshot.update(remaining, sensorStatus, measuredAt, receivedAt);
            }
            telemetry.save(snapshot);

            if ("NORMAL".equals(sensorStatus) && slot.isEnabled() && slot.getProductId() != null) {
                BigDecimal threshold = BigDecimal.valueOf(slot.getStockThreshold());
                if (remaining.compareTo(threshold) <= 0) {
                    alerts.record(machine, MachineAlertType.LOW_STOCK, MachineAlertSeverity.MEDIUM,
                        MachineAlertSource.DEVICE, slotId,
                        "Perfume level " + remaining.toPlainString() + "% is at or below "
                            + threshold.toPlainString() + "%", measuredAt);
                } else {
                    alerts.resolveObservedAlert(machine, MachineAlertType.LOW_STOCK, slotId,
                        MachineAlertSource.DEVICE, "Perfume level recovered above configured threshold",
                        measuredAt);
                }
            }
        }
    }

    private String requiredText(JsonNode payload, String field) {
        String value = payload.path(field).asText("").trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }

    private Long requiredLong(JsonNode payload, String field) {
        try {
            return Long.valueOf(requiredText(payload, field));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(field + " must be a numeric identifier", exception);
        }
    }
}
