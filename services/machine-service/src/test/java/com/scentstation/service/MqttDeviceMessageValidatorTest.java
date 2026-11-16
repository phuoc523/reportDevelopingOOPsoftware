package com.scentstation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.scentstation.config.MachineMqttProperties;
import com.scentstation.entity.DeviceRegistrationStatus;
import com.scentstation.entity.Machine;
import com.scentstation.mqtt.InvalidMqttMessageException;
import com.scentstation.repository.MachineRepository;
import com.scentstation.mqtt.DeviceMessageEnvelope;
import com.scentstation.mqtt.MqttTopicResolver;
import com.scentstation.mqtt.ValidatedDeviceMessage;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MqttDeviceMessageValidatorTest {
    private static final String DEVICE_ID = "device-01";
    private static final String TOPIC = "scentstation/v1/devices/" + DEVICE_ID + "/heartbeat";
    private static final Instant NOW = Instant.parse("2026-11-14T05:00:00Z");

    private final MachineRepository machines = mock(MachineRepository.class);
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final MachineMqttProperties properties = new MachineMqttProperties();
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private MqttDeviceMessageValidator validator;

    @BeforeEach
    void setUp() {
        properties.setMaxPayloadBytes(1024);
        properties.setAllowedClockSkewSeconds(300);
        validator = new MqttDeviceMessageValidator(
            machines, objectMapper, properties, new MqttMessageIdTracker(), clock);
    }

    @Test
    void validatesRegisteredDeviceAndDerivesTenantFromMachine() throws Exception {
        Machine machine = activeMachine();
        when(machines.findByDeviceIdentityIgnoreCase(DEVICE_ID)).thenReturn(java.util.Optional.of(machine));

        ValidatedDeviceMessage result = validator.validate(TOPIC, message(UUID.randomUUID().toString()));

        assertEquals("trusted-tenant", result.tenantId());
        assertEquals(machine, result.machine());
    }

    @Test
    void rejectsUnknownOrRevokedDevice() throws Exception {
        Machine revoked = activeMachine();
        revoked.setDeviceRegistrationStatus(DeviceRegistrationStatus.REVOKED);
        when(machines.findByDeviceIdentityIgnoreCase(DEVICE_ID)).thenReturn(java.util.Optional.of(revoked));

        assertThrows(InvalidMqttMessageException.class,
            () -> validator.validate(TOPIC, message(UUID.randomUUID().toString())));
    }

    @Test
    void rejectsUnknownDevice() throws Exception {
        when(machines.findByDeviceIdentityIgnoreCase(DEVICE_ID)).thenReturn(java.util.Optional.empty());

        assertThrows(InvalidMqttMessageException.class,
            () -> validator.validate(TOPIC, message(UUID.randomUUID().toString())));
    }

    @Test
    void rejectsPayloadDeviceIdentityThatDoesNotMatchTopic() throws Exception {
        when(machines.findByDeviceIdentityIgnoreCase(DEVICE_ID)).thenReturn(java.util.Optional.of(activeMachine()));

        assertThrows(InvalidMqttMessageException.class,
            () -> validator.validate(TOPIC, message(UUID.randomUUID().toString(), "device-02", "HEARTBEAT")));
    }

    @Test
    void rejectsWrongMessageTypeForTopic() throws Exception {
        when(machines.findByDeviceIdentityIgnoreCase(DEVICE_ID)).thenReturn(java.util.Optional.of(activeMachine()));

        assertThrows(InvalidMqttMessageException.class,
            () -> validator.validate(TOPIC, message(UUID.randomUUID().toString(), DEVICE_ID, "TELEMETRY")));
    }

    @Test
    void rejectsDuplicateMessageIds() throws Exception {
        when(machines.findByDeviceIdentityIgnoreCase(DEVICE_ID)).thenReturn(java.util.Optional.of(activeMachine()));
        byte[] payload = message("a4433b62-f809-4e51-9a7e-f2bc880db120");

        validator.validate(TOPIC, payload);
        assertThrows(InvalidMqttMessageException.class, () -> validator.validate(TOPIC, payload));
    }

    @Test
    void rejectsMalformedAndOversizedPayloads() {
        assertThrows(InvalidMqttMessageException.class,
            () -> validator.validate(TOPIC, "{".getBytes(java.nio.charset.StandardCharsets.UTF_8)));

        properties.setMaxPayloadBytes(1);
        assertThrows(InvalidMqttMessageException.class,
            () -> validator.validate(TOPIC, new byte[] {1, 2}));
    }

    @Test
    void rejectsUnsupportedSchemaAndStaleTimestamp() throws Exception {
        when(machines.findByDeviceIdentityIgnoreCase(DEVICE_ID)).thenReturn(java.util.Optional.of(activeMachine()));
        assertThrows(InvalidMqttMessageException.class,
            () -> validator.validate(TOPIC, message(UUID.randomUUID().toString(), DEVICE_ID, "HEARTBEAT", 2, NOW)));
        assertThrows(InvalidMqttMessageException.class,
            () -> validator.validate(TOPIC, message(UUID.randomUUID().toString(), DEVICE_ID, "HEARTBEAT",
                1, NOW.minusSeconds(301))));
    }

    @Test
    void rejectsTopicsOutsideAllowedDeviceChannels() {
        assertThrows(InvalidMqttMessageException.class,
            () -> MqttTopicResolver.parseDeviceTopic("scentstation/v1/devices/" + DEVICE_ID + "/commands"));
        assertThrows(InvalidMqttMessageException.class,
            () -> MqttTopicResolver.parseDeviceTopic("scentstation/v1/devices/device/../events"));
    }

    private Machine activeMachine() {
        Machine machine = new Machine();
        machine.setDeviceIdentity(DEVICE_ID);
        machine.setTenantId("trusted-tenant");
        machine.setDeviceRegistrationStatus(DeviceRegistrationStatus.ACTIVE);
        return machine;
    }

    private byte[] message(String id) throws Exception {
        return message(id, DEVICE_ID, "HEARTBEAT", 1, NOW);
    }

    private byte[] message(String id, String deviceId, String type) throws Exception {
        return message(id, deviceId, type, 1, NOW);
    }

    private byte[] message(String id, String deviceId, String type, int schemaVersion,
                           Instant timestamp) throws Exception {
        return objectMapper.writeValueAsBytes(new DeviceMessageEnvelope(
            schemaVersion, id, deviceId, type, timestamp,
            JsonNodeFactory.instance.objectNode()
                .put("status", "READY")
                .put("tenantId", "untrusted-payload-tenant")));
    }
}
