package com.scentstation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scentstation.config.MachineMqttProperties;
import com.scentstation.entity.DeviceRegistrationStatus;
import com.scentstation.entity.Machine;
import com.scentstation.mqtt.DeviceMessageEnvelope;
import com.scentstation.mqtt.InvalidMqttMessageException;
import com.scentstation.mqtt.MqttTopicResolver;
import com.scentstation.mqtt.ValidatedDeviceMessage;
import com.scentstation.repository.MachineRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class MqttDeviceMessageValidator {
    private final MachineRepository machines;
    private final ObjectMapper objectMapper;
    private final MachineMqttProperties properties;
    private final MqttMessageIdTracker messageIds;
    private final Clock clock;

    @Autowired
    public MqttDeviceMessageValidator(MachineRepository machines, ObjectMapper objectMapper,
                                      MachineMqttProperties properties) {
        this(machines, objectMapper, properties, new MqttMessageIdTracker(), Clock.systemUTC());
    }

    MqttDeviceMessageValidator(MachineRepository machines, ObjectMapper objectMapper,
                               MachineMqttProperties properties, MqttMessageIdTracker messageIds,
                               Clock clock) {
        this.machines = machines;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.messageIds = messageIds;
        this.clock = clock;
    }

    public ValidatedDeviceMessage validate(String topic, byte[] payload) {
        if (payload == null || payload.length == 0) {
            throw new InvalidMqttMessageException("MQTT payload is required");
        }
        if (payload.length > properties.getMaxPayloadBytes()) {
            throw new InvalidMqttMessageException("MQTT payload exceeds the configured size limit");
        }

        MqttTopicResolver.DeviceTopic parsedTopic = MqttTopicResolver.parseDeviceTopic(topic);
        DeviceMessageEnvelope envelope = parseEnvelope(payload);
        validateEnvelope(envelope, parsedTopic);

        Machine machine = machines.findByDeviceIdentityIgnoreCase(parsedTopic.deviceIdentity())
            .filter(candidate -> candidate.getDeviceRegistrationStatus() == DeviceRegistrationStatus.ACTIVE)
            .orElseThrow(() -> new InvalidMqttMessageException(
                "Device is unknown or its registration is not active"));
        if (!machine.getDeviceIdentity().equalsIgnoreCase(parsedTopic.deviceIdentity())) {
            throw new InvalidMqttMessageException("MQTT topic device identity does not match registration");
        }

        Instant now = clock.instant();
        Instant earliest = now.minus(properties.getAllowedClockSkewSeconds(), ChronoUnit.SECONDS);
        Instant latest = now.plus(properties.getAllowedClockSkewSeconds(), ChronoUnit.SECONDS);
        if (envelope.timestamp().isBefore(earliest) || envelope.timestamp().isAfter(latest)) {
            throw new InvalidMqttMessageException("MQTT message timestamp is outside the allowed clock skew");
        }
        if (!messageIds.markIfNew(machine.getDeviceIdentity(), envelope.messageId(), now)) {
            throw new InvalidMqttMessageException("Duplicate MQTT message ID");
        }

        return new ValidatedDeviceMessage(topic, envelope, machine, machine.getTenantId());
    }

    private DeviceMessageEnvelope parseEnvelope(byte[] payload) {
        try {
            return objectMapper.readValue(payload, DeviceMessageEnvelope.class);
        } catch (IOException | RuntimeException exception) {
            throw new InvalidMqttMessageException("MQTT payload is not a valid message envelope", exception);
        }
    }

    private static void validateEnvelope(DeviceMessageEnvelope envelope,
                                         MqttTopicResolver.DeviceTopic topic) {
        if (envelope == null || envelope.schemaVersion() != 1) {
            throw new InvalidMqttMessageException("Unsupported MQTT schema version");
        }
        if (envelope.messageId() == null
                || !envelope.messageId().matches(
                    "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-8][0-9a-fA-F]{3}-[89aAbB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}")) {
            throw new InvalidMqttMessageException("MQTT messageId must be a UUID");
        }
        try {
            UUID.fromString(envelope.messageId());
        } catch (IllegalArgumentException exception) {
            throw new InvalidMqttMessageException("MQTT messageId must be a UUID", exception);
        }
        if (envelope.deviceId() == null
                || !envelope.deviceId().equalsIgnoreCase(topic.deviceIdentity())) {
            throw new InvalidMqttMessageException("MQTT payload deviceId does not match its topic");
        }
        if (!MqttTopicResolver.expectedType(topic.channel()).equals(envelope.type())) {
            throw new InvalidMqttMessageException("MQTT message type does not match its topic");
        }
        if (envelope.timestamp() == null) {
            throw new InvalidMqttMessageException("MQTT timestamp is required");
        }
        if (envelope.payload() == null || !envelope.payload().isObject()) {
            throw new InvalidMqttMessageException("MQTT payload field must be a JSON object");
        }
    }
}
