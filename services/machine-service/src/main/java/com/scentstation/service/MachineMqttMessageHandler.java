package com.scentstation.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scentstation.config.MachineMqttProperties;
import com.scentstation.entity.DeviceRegistrationStatus;
import com.scentstation.entity.Machine;
import com.scentstation.entity.MachineCommand;
import com.scentstation.entity.MachineOperationalStatus;
import com.scentstation.mqtt.MqttEnvelope;
import com.scentstation.mqtt.MqttTopics;
import com.scentstation.repository.MachineCommandRepository;
import com.scentstation.repository.MachineRepository;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MachineMqttMessageHandler {
    private final ObjectMapper objectMapper;
    private final MachineRepository machines;
    private final MachineCommandRepository commands;
    private final MachineAlertService alerts;
    private final MachineMqttProperties properties;
    private final MqttMessageIdTracker messageIds;
    private final Clock clock;

    @Autowired
    public MachineMqttMessageHandler(ObjectMapper objectMapper, MachineRepository machines,
                                     MachineCommandRepository commands, MachineAlertService alerts,
                                     MachineMqttProperties properties) {
        this(objectMapper, machines, commands, alerts, properties,
            new MqttMessageIdTracker(), Clock.systemUTC());
    }

    MachineMqttMessageHandler(ObjectMapper objectMapper, MachineRepository machines,
                              MachineCommandRepository commands, MachineAlertService alerts,
                              MachineMqttProperties properties, MqttMessageIdTracker messageIds,
                              Clock clock) {
        this.objectMapper = objectMapper;
        this.machines = machines;
        this.commands = commands;
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

    private String requiredText(JsonNode payload, String field) {
        String value = payload.path(field).asText("").trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }
}
