package com.scentstation.simulator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.images.builder.Transferable;

@Testcontainers(disabledWithoutDocker = true)
class MqttDeviceSimulatorIntegrationTest {
    private static final String MACHINE_ID = "9101";
    private static final String COMMAND_TOPIC = "scentstation/9101/command";

    @Container
    static final GenericContainer<?> BROKER = new GenericContainer<>(DockerImageName.parse("eclipse-mosquitto:2.0"))
            .withExposedPorts(1883)
            .withCopyToContainer(Transferable.of("listener 1883\nallow_anonymous true\npersistence false\n"),
                    "/mosquitto-no-auth.conf")
            .withCommand("mosquitto", "-c", "/mosquitto-no-auth.conf");

    @TempDir Path temporaryDirectory;

    @Test
    void mqttV1HeartbeatTelemetryCommandAndDuplicateResponseRoundTrip() throws Exception {
        ObjectMapper mapper = MqttDeviceSimulator.newObjectMapper();
        BlockingQueue<String> received = new LinkedBlockingQueue<>();
        Mqtt3AsyncClient backendSide = MqttClient.builder().useMqttVersion3()
                .identifier("simulator-test-observer-" + UUID.randomUUID())
                .serverHost(BROKER.getHost()).serverPort(BROKER.getMappedPort(1883)).buildAsync();
        MqttDeviceSimulator simulator = null;
        try {
            backendSide.connectWith().cleanSession(true).send().get(10, TimeUnit.SECONDS);
            backendSide.subscribeWith().topicFilter("scentstation/9101/#").qos(MqttQos.AT_LEAST_ONCE)
                    .callback(message -> received.add(new String(message.getPayloadAsBytes(), StandardCharsets.UTF_8)))
                    .send().get(10, TimeUnit.SECONDS);

            var config = new SimulatorConfig(java.net.URI.create("tcp://" + BROKER.getHost() + ":" + BROKER.getMappedPort(1883)),
                    MACHINE_ID, "integration-device", "test-only", "test-1.0", 100, 100, 100,
                    0, temporaryDirectory.resolve("device-state.json"), SimulatorConfig.FaultMode.NORMAL,
                    List.of(new SimulatorConfig.SlotConfig("91001", "91001", 0.12, 1.0, 18, 0.85, "NORMAL")));
            DeviceStateStore store = new DeviceStateStore(mapper, config.stateFile());
            SimulatorDeviceState state = new SimulatorDeviceState(config.slots(), store, config.initialMode());
            simulator = new MqttDeviceSimulator(config, mapper, store, state);
            simulator.start();

            awaitType(received, mapper, "HEARTBEAT");
            awaitType(received, mapper, "TELEMETRY");
            String commandId = "integration-" + UUID.randomUUID();
            String command = mapper.writeValueAsString(Map.of("schemaVersion", 1,
                    "messageId", UUID.randomUUID().toString(), "machineId", MACHINE_ID,
                    "type", "DISPENSE_COMMAND", "timestamp", Instant.now().toString(),
                    "payload", Map.of("commandId", commandId, "action", "DISPENSE", "slotId", "91001",
                            "durationMs", 200, "expiresAt", Instant.now().plusSeconds(30).toString())));
            publish(backendSide, COMMAND_TOPIC, command);

            JsonNode result = awaitCommandResult(received, mapper, commandId);
            assertEquals("DISPENSED", result.path("payload").path("status").asText());
            assertEquals(0.88, state.slot("91001").remainingMl(), 0.0001);

            publish(backendSide, COMMAND_TOPIC, command);
            JsonNode duplicateResult = awaitCommandResult(received, mapper, commandId);
            assertEquals(result.path("messageId").asText(), duplicateResult.path("messageId").asText());
            assertEquals(0.88, state.slot("91001").remainingMl(), 0.0001,
                    "QoS 1 redelivery must not actuate the same command twice");
        } finally {
            if (simulator != null) simulator.close();
            backendSide.disconnect().get(5, TimeUnit.SECONDS);
        }
    }

    private void publish(Mqtt3AsyncClient client, String topic, String json) throws Exception {
        client.publishWith().topic(topic).qos(MqttQos.AT_LEAST_ONCE).retain(false)
                .payload(json.getBytes(StandardCharsets.UTF_8)).send().get(10, TimeUnit.SECONDS);
    }

    private void awaitType(BlockingQueue<String> queue, ObjectMapper mapper, String type) throws Exception {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
        while (Instant.now().isBefore(deadline)) {
            String raw = queue.poll(200, TimeUnit.MILLISECONDS);
            if (raw != null && type.equals(mapper.readTree(raw).path("type").asText())) return;
        }
        throw new AssertionError("Timed out waiting for MQTT " + type);
    }

    private JsonNode awaitCommandResult(BlockingQueue<String> queue, ObjectMapper mapper, String commandId) throws Exception {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
        while (Instant.now().isBefore(deadline)) {
            String raw = queue.poll(200, TimeUnit.MILLISECONDS);
            if (raw == null) continue;
            JsonNode tree = mapper.readTree(raw);
            if ("COMMAND_RESPONSE".equals(tree.path("type").asText())
                    && commandId.equals(tree.path("payload").path("commandId").asText())
                    && "DISPENSED".equals(tree.path("payload").path("status").asText())) return tree;
        }
        throw new AssertionError("Timed out waiting for DISPENSED response for " + commandId);
    }
}
