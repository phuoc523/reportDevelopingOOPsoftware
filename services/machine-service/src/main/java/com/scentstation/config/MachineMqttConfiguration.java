package com.scentstation.config;

import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(MachineMqttProperties.class)
public class MachineMqttConfiguration {
    @Bean
    @ConditionalOnProperty(prefix = "machine.mqtt", name = "enabled", havingValue = "true")
    MqttAsyncClient mqttAsyncClient(MachineMqttProperties properties) throws MqttException {
        requireConfigured(properties.getBrokerUrl(), "machine.mqtt.broker-url");
        requireConfigured(properties.getClientId(), "machine.mqtt.client-id");
        requireConfigured(properties.getUsername(), "machine.mqtt.username");
        requireConfigured(properties.getPassword(), "machine.mqtt.password");
        return new MqttAsyncClient(properties.getBrokerUrl(), properties.getClientId(),
            new MemoryPersistence());
    }

    @Bean
    @ConditionalOnProperty(prefix = "machine.mqtt", name = "enabled", havingValue = "true")
    MqttConnectOptions mqttConnectOptions(MachineMqttProperties properties) {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setUserName(properties.getUsername());
        options.setPassword(properties.getPassword().toCharArray());
        options.setCleanSession(true);
        options.setConnectionTimeout(properties.getConnectionTimeoutSeconds());
        options.setKeepAliveInterval(properties.getKeepAliveIntervalSeconds());
        options.setMaxInflight(20);
        return options;
    }

    private static void requireConfigured(String value, String propertyName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(propertyName + " must be configured when MQTT is enabled");
        }
    }
}
