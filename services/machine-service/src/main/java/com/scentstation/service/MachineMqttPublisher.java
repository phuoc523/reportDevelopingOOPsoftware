package com.scentstation.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scentstation.entity.DeviceRegistrationStatus;
import com.scentstation.entity.Machine;
import com.scentstation.mqtt.DeviceMessageEnvelope;
import com.scentstation.mqtt.MqttTopicResolver;
import com.scentstation.repository.MachineRepository;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
@ConditionalOnProperty(prefix = "machine.mqtt", name = "enabled", havingValue = "true")
public class MachineMqttPublisher {
    private static final Logger logger = LoggerFactory.getLogger(MachineMqttPublisher.class);
    private static final int QOS = 1;

    private final MqttAsyncClient client;
    private final MachineRepository machines;
    private final ObjectMapper objectMapper;

    public MachineMqttPublisher(MqttAsyncClient client, MachineRepository machines,
                                ObjectMapper objectMapper) {
        this.client = client;
        this.machines = machines;
        this.objectMapper = objectMapper;
    }

    public void publishConnectivityTest(String deviceIdentity) {
        Machine machine = machines.findByDeviceIdentityIgnoreCase(deviceIdentity)
            .filter(candidate -> candidate.getDeviceRegistrationStatus() == DeviceRegistrationStatus.ACTIVE)
            .orElseThrow(() -> new IllegalArgumentException("Device is unknown or not active"));
        String topic = MqttTopicResolver.deviceTestTopic(machine.getDeviceIdentity());
        if (!client.isConnected()) {
            throw new IllegalStateException("MQTT publisher is not connected to the broker");
        }

        DeviceMessageEnvelope envelope = new DeviceMessageEnvelope(
            1,
            UUID.randomUUID().toString(),
            machine.getDeviceIdentity(),
            "CONNECTIVITY_TEST",
            Instant.now(),
            objectMapper.valueToTree(Map.of("status", "READY"))
        );
        try {
            MqttMessage message = new MqttMessage(objectMapper.writeValueAsBytes(envelope));
            message.setQos(QOS);
            message.setRetained(false);
            client.publish(topic, message, null, new IMqttActionListener() {
                @Override
                public void onSuccess(org.eclipse.paho.client.mqttv3.IMqttToken token) {
                    logger.info("Published MQTT connectivity test for device {}", machine.getDeviceIdentity());
                }

                @Override
                public void onFailure(org.eclipse.paho.client.mqttv3.IMqttToken token, Throwable cause) {
                    logger.error("Unable to publish MQTT connectivity test for device {}",
                        machine.getDeviceIdentity(), cause);
                }
            });
        } catch (JsonProcessingException | MqttException exception) {
            throw new IllegalStateException("Unable to publish MQTT connectivity test", exception);
        }
    }
}
