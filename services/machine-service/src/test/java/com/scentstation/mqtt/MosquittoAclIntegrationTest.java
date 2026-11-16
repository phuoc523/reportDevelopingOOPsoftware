package com.scentstation.mqtt;

import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
@EnabledIfSystemProperty(named = "machine.mqtt.broker-tests", matches = "true")
class MosquittoAclIntegrationTest {
    private static final String DEVICE_A = "device-a";
    private static final String DEVICE_B = "device-b";
    private static final String BACKEND = "machine-service";
    private static final String DEVICE_A_PASSWORD = "test-device-a-password";
    private static final String DEVICE_B_PASSWORD = "test-device-b-password";
    private static final String BACKEND_PASSWORD = "test-backend-password";
    private static final String START_SCRIPT = """
        mosquitto_passwd -b -c /tmp/passwd machine-service test-backend-password &&
        mosquitto_passwd -b /tmp/passwd device-a test-device-a-password &&
        mosquitto_passwd -b /tmp/passwd device-b test-device-b-password &&
        mosquitto -c /tmp/mosquitto.conf
        """;

    @Container
    private static final GenericContainer<?> BROKER =
        new GenericContainer<>(DockerImageName.parse("eclipse-mosquitto:2.0.22"))
            .withExposedPorts(1883)
            .withCopyToContainer(MountableFile.forClasspathResource("mqtt-test-acl"), "/tmp/acl")
            .withCopyToContainer(MountableFile.forClasspathResource("mqtt-test.conf"), "/tmp/mosquitto.conf")
            .withCreateContainerCmdModifier(command -> {
                command.withEntrypoint("/bin/sh");
                command.withCmd("-c", START_SCRIPT);
            })
            .waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofSeconds(30)));

    private MqttAsyncClient deviceA;
    private MqttAsyncClient deviceB;
    private MqttAsyncClient backend;

    @BeforeEach
    void connectAuthorizedClients() throws Exception {
        deviceA = connect(DEVICE_A, DEVICE_A_PASSWORD);
        deviceB = connect(DEVICE_B, DEVICE_B_PASSWORD);
        backend = connect(BACKEND, BACKEND_PASSWORD);
    }

    @AfterEach
    void disconnectClients() {
        close(deviceA);
        close(deviceB);
        close(backend);
    }

    @Test
    void deniesAnonymousAndInvalidPasswordConnections() throws Exception {
        MqttAsyncClient anonymous = createClient("anonymous");
        try {
            assertThrows(MqttException.class, () -> anonymous.connect(new MqttConnectOptions())
                .waitForCompletion(5000));
        } finally {
            close(anonymous);
        }

        MqttAsyncClient invalid = createClient(DEVICE_A);
        MqttConnectOptions options = options(DEVICE_A, "wrong-password");
        try {
            assertThrows(MqttException.class, () -> invalid.connect(options).waitForCompletion(5000));
        } finally {
            close(invalid);
        }
    }

    @Test
    void acceptsOwnDeviceMessagesAndDeniesCrossDeviceSubscriptions() throws Exception {
        CountDownLatch received = new CountDownLatch(1);
        backend.subscribe(topic(DEVICE_A, "heartbeat"), 1, (topic, message) -> received.countDown())
            .waitForCompletion(5000);
        publish(deviceA, topic(DEVICE_A, "heartbeat"), "heartbeat");
        assertTrue(received.await(3, TimeUnit.SECONDS));

        IMqttToken deniedSubscription = deviceA.subscribe(topic(DEVICE_B, "heartbeat"), 1);
        deniedSubscription.waitForCompletion(5000);
        assertArrayEquals(new int[] {128}, deniedSubscription.getGrantedQos());
    }

    @Test
    void deviceCannotPublishToAnotherDevicesTopic() throws Exception {
        CountDownLatch crossDeviceMessageReceived = new CountDownLatch(1);
        backend.subscribe(topic(DEVICE_B, "heartbeat"), 1,
            (ignoredTopic, ignoredMessage) -> crossDeviceMessageReceived.countDown()).waitForCompletion(5000);
        try {
            publish(deviceA, topic(DEVICE_B, "heartbeat"), "must be denied");
        } catch (MqttException ignored) {
            // Mosquitto may reject the publish by disconnecting the unauthorized client.
        }
        assertFalse(crossDeviceMessageReceived.await(700, TimeUnit.MILLISECONDS));
    }

    @Test
    void devicesCannotPublishCommandsAndBackendCanPublishOnlyAuthorizedTopics() throws Exception {
        CountDownLatch commandMessageReceived = new CountDownLatch(1);
        deviceA.subscribe(topic(DEVICE_A, "commands"), 1,
            (ignoredTopic, ignoredMessage) -> commandMessageReceived.countDown()).waitForCompletion(5000);
        publish(deviceA, topic(DEVICE_A, "commands"), "must be denied");
        assertFalse(commandMessageReceived.await(700, TimeUnit.MILLISECONDS));

        CountDownLatch backendTestReceived = new CountDownLatch(1);
        deviceA.subscribe(topic(DEVICE_A, "backend/test"), 1,
            (ignoredTopic, ignoredMessage) -> backendTestReceived.countDown()).waitForCompletion(5000);
        publish(backend, topic(DEVICE_A, "backend/test"), "CONNECTIVITY_TEST");
        assertTrue(backendTestReceived.await(3, TimeUnit.SECONDS));

        IMqttToken deniedBackendSubscription = backend.subscribe(topic(DEVICE_A, "commands"), 1);
        deniedBackendSubscription.waitForCompletion(5000);
        assertArrayEquals(new int[] {128}, deniedBackendSubscription.getGrantedQos());
    }

    @Test
    void brokerRejectsRetainedMessages() throws Exception {
        MqttMessage retained = new MqttMessage("retained".getBytes(StandardCharsets.UTF_8));
        retained.setQos(1);
        retained.setRetained(true);
        assertThrows(MqttException.class, () -> deviceA.publish(topic(DEVICE_A, "events"), retained)
            .waitForCompletion(5000));
    }

    @Test
    void brokerRejectsOversizedMessages() throws Exception {
        byte[] oversizedPayload = new byte[65_537];
        MqttMessage oversized = new MqttMessage(oversizedPayload);
        oversized.setQos(1);
        assertThrows(MqttException.class, () -> deviceA.publish(topic(DEVICE_A, "events"), oversized)
            .waitForCompletion(5000));
    }

    private static MqttAsyncClient connect(String username, String password) throws Exception {
        MqttAsyncClient client = createClient(username);
        client.connect(options(username, password)).waitForCompletion(5000);
        return client;
    }

    private static MqttAsyncClient createClient(String clientIdPrefix) throws MqttException {
        return new MqttAsyncClient(
            "tcp://" + BROKER.getHost() + ":" + BROKER.getMappedPort(1883),
            clientIdPrefix + "-" + UUID.randomUUID(),
            new MemoryPersistence());
    }

    private static MqttConnectOptions options(String username, String password) {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setCleanSession(true);
        options.setConnectionTimeout(5);
        if (username != null) {
            options.setUserName(username);
            options.setPassword(password.toCharArray());
        }
        return options;
    }

    private static void publish(MqttAsyncClient client, String topic, String payload) throws Exception {
        MqttMessage message = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
        message.setQos(1);
        client.publish(topic, message).waitForCompletion(5000);
    }

    private static String topic(String deviceId, String channel) {
        return "scentstation/v1/devices/" + deviceId + "/" + channel;
    }

    private static void close(MqttAsyncClient client) {
        if (client == null) {
            return;
        }
        try {
            if (client.isConnected()) {
                client.disconnect().waitForCompletion(3000);
            }
            client.close();
        } catch (MqttException ignored) {
        }
    }
}
