package com.scentstation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scentstation.mqtt.DispenseCommandCreatedEvent;
import com.scentstation.mqtt.MqttEnvelope;
import com.scentstation.mqtt.MqttTopics;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@ConditionalOnProperty(prefix = "machine.mqtt", name = "enabled", havingValue = "true")
public class MachineMqttGateway {
    private static final Logger logger = LoggerFactory.getLogger(MachineMqttGateway.class);
    private static final int QOS = 1;

    private final MqttAsyncClient client;
    private final MqttConnectOptions connectOptions;
    private final MachineMqttMessageHandler handler;
    private final ObjectMapper objectMapper;
    private final AtomicBoolean connecting = new AtomicBoolean();

    public MachineMqttGateway(MqttAsyncClient client, MqttConnectOptions connectOptions,
                              MachineMqttMessageHandler handler, ObjectMapper objectMapper) {
        this.client = client;
        this.connectOptions = connectOptions;
        this.handler = handler;
        this.objectMapper = objectMapper;
        client.setCallback(new MqttCallbackExtended() {
            @Override
            public void connectComplete(boolean reconnect, String serverURI) {
                subscribe();
            }

            @Override
            public void connectionLost(Throwable cause) {
                logger.warn("MQTT connection lost; reconnect is scheduled", cause);
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) {
                receive(topic, message);
            }

            @Override
            public void deliveryComplete(org.eclipse.paho.client.mqttv3.IMqttDeliveryToken token) {
            }
        });
    }

    @EventListener(ApplicationReadyEvent.class)
    public void connectOnStartup() {
        connectIfDisconnected();
    }

    @Scheduled(fixedDelayString = "${machine.mqtt.reconnect-interval-ms:5000}")
    public void connectIfDisconnected() {
        if (client.isConnected() || !connecting.compareAndSet(false, true)) {
            return;
        }
        try {
            client.connect(connectOptions, null, new IMqttActionListener() {
                @Override
                public void onSuccess(org.eclipse.paho.client.mqttv3.IMqttToken token) {
                    connecting.set(false);
                    logger.info("Connected to MQTT broker {}", client.getServerURI());
                }

                @Override
                public void onFailure(org.eclipse.paho.client.mqttv3.IMqttToken token, Throwable cause) {
                    connecting.set(false);
                    logger.warn("Unable to connect to MQTT broker; retry is scheduled", cause);
                }
            });
        } catch (MqttException exception) {
            connecting.set(false);
            logger.warn("Unable to start MQTT connection", exception);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(DispenseCommandCreatedEvent event) {
        if (!client.isConnected()) {
            logger.error("MQTT command {} was committed while broker was disconnected", event.commandId());
            return;
        }
        MqttEnvelope envelope = new MqttEnvelope(
            1,
            UUID.randomUUID().toString(),
            Long.toString(event.machineId()),
            "DISPENSE_COMMAND",
            event.issuedAt(),
            objectMapper.valueToTree(Map.of(
                "commandId", event.commandId(),
                "action", "DISPENSE",
                "slotId", event.slotId(),
                "durationMs", event.durationMs(),
                "expiresAt", event.expiresAt()
            ))
        );
        try {
            MqttMessage message = new MqttMessage(objectMapper.writeValueAsBytes(envelope));
            message.setQos(QOS);
            message.setRetained(false);
            client.publish(MqttTopics.command(event.machineId()), message);
        } catch (Exception exception) {
            logger.error("Unable to publish MQTT command {}", event.commandId(), exception);
        }
    }

    public boolean isConnected() {
        return client.isConnected();
    }

    private void subscribe() {
        try {
            int[] qos = java.util.stream.IntStream.range(0, MqttTopics.INBOUND_FILTERS.length)
                .map(ignored -> QOS).toArray();
            client.subscribe(MqttTopics.INBOUND_FILTERS, qos);
            logger.info("Subscribed to MQTT device response, heartbeat, telemetry, event, and alert topics");
        } catch (MqttException exception) {
            logger.error("Unable to subscribe to MQTT device topics", exception);
        }
    }

    private void receive(String topic, MqttMessage message) {
        try {
            handler.handle(topic, message.getPayload());
        } catch (RuntimeException exception) {
            logger.warn("Rejected MQTT message on {}: {}", topic, exception.getMessage());
        }
    }
}
