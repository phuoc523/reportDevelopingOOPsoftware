package com.scentstation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scentstation.dto.request.DeviceMessageRequest;
import com.scentstation.exception.InvalidOperationException;
import com.scentstation.repository.MachineCommandRepository;
import com.scentstation.repository.MachineRepository;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.env.MockEnvironment;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

class MqttDeviceTopicTest {
    @Test
    void decodesDeviceIdentityFromBase64UrlTopic() {
        String identity = "machine/001 +中文";
        String topic = "scentstation/v1/devices/"
            + MqttDeviceTopic.encodeIdentity(identity) + "/telemetry";

        MqttDeviceTopic parsed = MqttDeviceTopic.parse(topic);

        assertEquals(identity, parsed.getDeviceIdentity());
        assertEquals("telemetry", parsed.getMessageType());
    }

    @Test
    void parsesDedicatedCommandAcknowledgementAndResultTopics() {
        String token = MqttDeviceTopic.encodeIdentity("machine-001");

        MqttDeviceTopic ack = MqttDeviceTopic.parse(
            "scentstation/v1/devices/" + token + "/commands/cmd-12/ack");
        MqttDeviceTopic result = MqttDeviceTopic.parse(
            "scentstation/v1/devices/" + token + "/commands/cmd-12/result");

        assertEquals("cmd-12", ack.getCommandId());
        assertEquals("ack", ack.getMessageType());
        assertEquals("result", result.getMessageType());
    }

    @Test
    void rejectsNonCanonicalOrMalformedDeviceTokensAndTopics() {
        assertThrows(IllegalArgumentException.class,
            () -> MqttDeviceTopic.parse("scentstation/v1/devices/not-base64/heartbeat"));
        assertThrows(IllegalArgumentException.class,
            () -> MqttDeviceTopic.parse("scentstation/v1/devices/"
                + MqttDeviceTopic.encodeIdentity("machine-1") + "/commands/cmd-1/unknown"));
    }

    @Test
    void requiresTlsAndBrokerCredentialsWhenMqttIsEnabled() {
        MqttDeviceMessageListener.validateBrokerConfiguration(
            "ssl://broker.example:8883", "ingestion-service", "secret");

        IllegalStateException tlsError = assertThrows(IllegalStateException.class,
            () -> MqttDeviceMessageListener.validateBrokerConfiguration(
                "tcp://broker.example:1883", "ingestion-service", "secret"));
        assertTrue(tlsError.getMessage().contains("TLS"));
        assertThrows(IllegalStateException.class,
            () -> MqttDeviceMessageListener.validateBrokerConfiguration(
                "ssl://broker.example:8883", "", ""));
    }

    @Test
    void dropsBusinessAndMalformedPayloadErrorsWithoutEscapingCallback() throws Exception {
        DeviceMessageProcessor processor = mock(DeviceMessageProcessor.class);
        ObjectMapper mapper = objectMapper();
        MqttDeviceMessageListener listener = listener(processor, mapper);
        DeviceMessageRequest request = message();
        String identity = request.getDeviceIdentity();
        doThrow(new InvalidOperationException("device is revoked"))
            .when(processor).heartbeat(eq(identity), any(DeviceMessageRequest.class));
        MqttMessage payload = new MqttMessage(mapper.writeValueAsBytes(request));
        String topic = heartbeatTopic(identity);

        assertDoesNotThrow(() -> listener.processCallbackMessage(topic, payload));
        assertDoesNotThrow(() -> listener.processCallbackMessage(topic, new MqttMessage("{".getBytes())));
        verify(processor).heartbeat(eq(identity), any(DeviceMessageRequest.class));
    }

    @Test
    void propagatesInfrastructureFailuresToMqttCallback() {
        DeviceMessageProcessor processor = mock(DeviceMessageProcessor.class);
        ObjectMapper mapper = objectMapper();
        MqttDeviceMessageListener listener = listener(processor, mapper);
        DeviceMessageRequest request = message();
        doThrow(new DataAccessResourceFailureException("database unavailable"))
            .when(processor).heartbeat(eq(request.getDeviceIdentity()), any(DeviceMessageRequest.class));
        MqttMessage payload;
        try {
            payload = new MqttMessage(mapper.writeValueAsBytes(request));
        } catch (Exception exception) {
            throw new AssertionError("Could not serialize test message", exception);
        }

        assertThrows(DataAccessResourceFailureException.class,
            () -> listener.processCallbackMessage(heartbeatTopic(request.getDeviceIdentity()), payload));
    }

    private MqttDeviceMessageListener listener(DeviceMessageProcessor processor, ObjectMapper mapper) {
        return new MqttDeviceMessageListener(mapper, processor,
            mock(MachineRepository.class), mock(MachineCommandRepository.class),
            new MockEnvironment());
    }

    private ObjectMapper objectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }

    private DeviceMessageRequest message() {
        DeviceMessageRequest request = new DeviceMessageRequest();
        request.setEventId("evt-1");
        request.setDeviceIdentity("machine-001");
        request.setTenantId("tenant-1");
        request.setOccurredAt(Instant.now());
        return request;
    }

    private String heartbeatTopic(String identity) {
        return "scentstation/v1/devices/" + MqttDeviceTopic.encodeIdentity(identity) + "/heartbeat";
    }
}
