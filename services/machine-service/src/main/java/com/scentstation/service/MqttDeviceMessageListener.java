package com.scentstation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.scentstation.exception.ConflictException;
import com.scentstation.dto.request.DeviceCommandMessage;
import com.scentstation.dto.request.DeviceMessageRequest;
import com.scentstation.entity.DeviceRegistrationStatus;
import com.scentstation.entity.Machine;
import com.scentstation.entity.MachineCommand;
import com.scentstation.exception.InvalidOperationException;
import com.scentstation.exception.ResourceNotFoundException;
import com.scentstation.repository.MachineRepository;
import com.scentstation.repository.MachineCommandRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "machine.iot.mqtt", name = "enabled", havingValue = "true")
public class MqttDeviceMessageListener {
    private static final Logger logger = LoggerFactory.getLogger(MqttDeviceMessageListener.class);
    private static final String[] TOPIC_FILTERS = {
        "scentstation/v1/devices/+/heartbeat",
        "scentstation/v1/devices/+/telemetry",
        "scentstation/v1/devices/+/events",
        "scentstation/v1/devices/+/commands/+/ack",
        "scentstation/v1/devices/+/commands/+/result"
    };

    private final ObjectMapper objectMapper;
    private final DeviceMessageProcessor processor;
    private final MachineRepository machines;
    private final MachineCommandRepository commands;
    private final String brokerUri;
    private final String username;
    private final String password;
    private final String clientId;
    private MqttClient client;

    public MqttDeviceMessageListener(ObjectMapper objectMapper, DeviceMessageProcessor processor,
                                     MachineRepository machines,
                                     MachineCommandRepository commands,
                                     org.springframework.core.env.Environment environment) {
        this.objectMapper = objectMapper;
        this.processor = processor;
        this.machines = machines;
        this.commands = commands;
        this.brokerUri = environment.getProperty("machine.iot.mqtt.broker-uri", "");
        this.username = environment.getProperty("machine.iot.mqtt.username", "");
        this.password = environment.getProperty("machine.iot.mqtt.password", "");
        this.clientId = environment.getProperty("machine.iot.mqtt.client-id",
            "machine-service-ingestion");
    }

    @PostConstruct
    public void connect() throws MqttException {
        validateBrokerConfiguration(brokerUri, username, password);
        client = new MqttClient(brokerUri, clientId, new MemoryPersistence());
        client.setCallback(new MqttCallbackExtended() {
            @Override
            public void connectComplete(boolean reconnect, String serverURI) {
                subscribe();
            }

            @Override
            public void connectionLost(Throwable cause) {
                logger.error("MQTT connection lost; automatic reconnect is enabled", cause);
            }

            @Override
            public void messageArrived(String topic, MqttMessage mqttMessage) throws Exception {
                processCallbackMessage(topic, mqttMessage);
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
                // This client subscribes to device messages and does not publish messages.
            }
        });

        MqttConnectOptions options = new MqttConnectOptions();
        options.setAutomaticReconnect(true);
        options.setCleanSession(false);
        options.setConnectionTimeout(10);
        options.setKeepAliveInterval(30);
        options.setUserName(username);
        options.setPassword(password.toCharArray());
        client.connect(options);
    }

    @PreDestroy
    public void disconnect() throws MqttException {
        if (client != null && client.isConnected()) {
            client.disconnect();
        }
        if (client != null) {
            client.close();
        }
    }

    @Transactional
    public void publishCommand(String deviceIdentity, String tenantId, String commandId,
                               String commandType, Map<String, Object> parameters,
                               Instant expiresAt) throws MqttException, JsonProcessingException {
        if (deviceIdentity == null || deviceIdentity.isBlank() || deviceIdentity.length() > 150) {
            throw new InvalidOperationException("deviceIdentity must contain between 1 and 150 characters");
        }
        if (commandId == null || !commandId.matches("[A-Za-z0-9._-]{1,100}")
                || commandType == null || commandType.isBlank() || commandType.length() > 50) {
            throw new InvalidOperationException("commandId must use only letters, digits, dot, underscore or hyphen; commandType is required and limited to 50 characters");
        }
        Machine machine = machines.findByDeviceIdentityForUpdate(deviceIdentity)
            .orElseThrow(() -> new ResourceNotFoundException("Device is not registered"));
        if (machine.getDeviceRegistrationStatus() != DeviceRegistrationStatus.ACTIVE) {
            throw new InvalidOperationException("Cannot send a command to a revoked device");
        }
        if (tenantId == null || !machine.getTenantId().equals(tenantId.trim())) {
            throw new InvalidOperationException("Device tenant does not match its registered tenant");
        }
        Instant issuedAt = Instant.now();
        if (expiresAt == null || !expiresAt.isAfter(issuedAt)) {
            throw new InvalidOperationException("Command expiry must be in the future");
        }
        if (client == null || !client.isConnected()) {
            throw new IllegalStateException("MQTT broker connection is unavailable");
        }
        if (commands.existsByCommandId(commandId)) {
            throw new ConflictException("commandId has already been used");
        }

        DeviceCommandMessage command = new DeviceCommandMessage(commandId, machine.getDeviceIdentity(),
            machine.getTenantId(), commandType.trim(),
            parameters == null ? Map.of() : new java.util.LinkedHashMap<>(parameters),
            issuedAt, expiresAt);
        byte[] payload = objectMapper.writeValueAsBytes(command);
        String topic = "scentstation/v1/devices/"
            + MqttDeviceTopic.encodeIdentity(machine.getDeviceIdentity()) + "/commands";
        commands.saveAndFlush(new MachineCommand(commandId, machine, commandType.trim(),
            issuedAt, expiresAt));
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                publishCommandMessage(topic, payload);
            }
        });
    }

    private void publishCommandMessage(String topic, byte[] payload) {
        if (client == null || !client.isConnected()) {
            logger.error("Command was committed but MQTT is unavailable; command remains pending until expiry");
            return;
        }
        MqttMessage mqttMessage = new MqttMessage(payload);
        mqttMessage.setQos(1);
        mqttMessage.setRetained(false);
        try {
            client.publish(topic, mqttMessage);
        } catch (MqttException exception) {
            logger.error("Command was committed but could not be published; command remains pending until expiry",
                exception);
        }
    }

    static void validateBrokerConfiguration(String brokerUri, String username, String password) {
        if (brokerUri == null || !brokerUri.startsWith("ssl://")) {
            throw new IllegalStateException("MQTT must use ssl://; configure a broker with TLS");
        }
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new IllegalStateException("MQTT username and password are required when MQTT is enabled");
        }
    }

    void processCallbackMessage(String topic, MqttMessage mqttMessage) throws Exception {
        try {
            handleMessage(topic, mqttMessage);
        } catch (JsonProcessingException | IllegalArgumentException
                 | InvalidOperationException | ConflictException
                 | ResourceNotFoundException exception) {
            logger.warn("Dropping invalid MQTT message from topic {}: {}",
                topic, exception.getMessage());
        }
    }

    private void subscribe() {
        try {
            client.subscribe(TOPIC_FILTERS, new int[] {1, 1, 1, 1, 1});
        } catch (MqttException exception) {
            logger.error("Unable to subscribe to machine device MQTT topics", exception);
        }
    }

    private void handleMessage(String topic, MqttMessage mqttMessage) throws Exception {
        MqttDeviceTopic deviceTopic = MqttDeviceTopic.parse(topic);
        DeviceMessageRequest request = objectMapper.readValue(
            mqttMessage.getPayload(),
            DeviceMessageRequest.class);
        String messageType = deviceTopic.getMessageType();
        if (messageType.equals("ack") || messageType.equals("result")) {
            if (request.getCommandId() == null
                    || !request.getCommandId().equals(deviceTopic.getCommandId())) {
                throw new InvalidOperationException("Topic commandId does not match the message");
            }
            request.setEventType(messageType.equals("ack") ? "COMMAND_ACK" : "COMMAND_RESULT");
            processor.deviceEvent(deviceTopic.getDeviceIdentity(), request);
            return;
        }
        switch (messageType) {
            case "heartbeat" -> processor.heartbeat(deviceTopic.getDeviceIdentity(), request);
            case "telemetry" -> processor.telemetry(deviceTopic.getDeviceIdentity(), request);
            case "events" -> processor.deviceEvent(deviceTopic.getDeviceIdentity(), request);
            default -> throw new IllegalArgumentException("Unsupported machine MQTT message type");
        }
    }
}
