package com.scentstation.service;

import com.scentstation.config.MachineMqttProperties;
import com.scentstation.mqtt.InvalidMqttMessageException;
import com.scentstation.mqtt.MqttTopicResolver;
import com.scentstation.mqtt.ValidatedDeviceMessage;
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

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@ConditionalOnProperty(prefix = "machine.mqtt", name = "enabled", havingValue = "true")
public class MachineMqttConnection {
    private static final Logger logger = LoggerFactory.getLogger(MachineMqttConnection.class);
    private static final int QOS = 1;

    private final MqttAsyncClient client;
    private final MqttConnectOptions connectOptions;
    private final MqttDeviceMessageValidator validator;
    private final AtomicBoolean connecting = new AtomicBoolean();

    public MachineMqttConnection(MqttAsyncClient client, MqttConnectOptions connectOptions,
                                 MqttDeviceMessageValidator validator) {
        this.client = client;
        this.connectOptions = connectOptions;
        this.validator = validator;
        this.client.setCallback(new MqttCallbackExtended() {
            @Override
            public void connectComplete(boolean reconnect, String serverURI) {
                subscribeToDeviceMessages(reconnect);
            }

            @Override
            public void connectionLost(Throwable cause) {
                if (cause == null) {
                    logger.warn("MQTT connection was lost; a scheduled reconnect will be attempted");
                } else {
                    logger.warn("MQTT connection was lost; a scheduled reconnect will be attempted", cause);
                }
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
    public void connectWhenApplicationIsReady() {
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
                    logger.warn("Unable to connect to MQTT broker; will retry on the configured interval", cause);
                }
            });
        } catch (MqttException exception) {
            connecting.set(false);
            logger.warn("Unable to start MQTT connection; will retry on the configured interval", exception);
        }
    }

    private void subscribeToDeviceMessages(boolean reconnect) {
        String[] topics = MqttTopicResolver.DEVICE_INBOUND_FILTER.split(",");
        int[] qos = new int[topics.length];
        Arrays.fill(qos, QOS);
        try {
            client.subscribe(topics, qos, null, new IMqttActionListener() {
                @Override
                public void onSuccess(org.eclipse.paho.client.mqttv3.IMqttToken token) {
                    logger.info("Subscribed to device MQTT message topics after {}",
                        reconnect ? "reconnection" : "connection");
                }

                @Override
                public void onFailure(org.eclipse.paho.client.mqttv3.IMqttToken token, Throwable cause) {
                    logger.error("Unable to subscribe to device MQTT message topics", cause);
                }
            });
        } catch (MqttException exception) {
            logger.error("Unable to subscribe to device MQTT message topics", exception);
        }
    }

    private void receive(String topic, MqttMessage message) {
        try {
            ValidatedDeviceMessage validated = validator.validate(topic, message.getPayload());
            logger.info("Accepted MQTT message id={} device={} tenant={} channel={}",
                validated.envelope().messageId(), validated.machine().getDeviceIdentity(),
                validated.tenantId(), MqttTopicResolver.parseDeviceTopic(topic).channel());
        } catch (InvalidMqttMessageException exception) {
            logger.warn("Rejected MQTT message on topic {}: {}", topic, exception.getMessage());
        } catch (RuntimeException exception) {
            logger.error("Failed to process MQTT message on topic {}", topic, exception);
        }
    }

    public boolean isConnected() {
        return client.isConnected();
    }
}
