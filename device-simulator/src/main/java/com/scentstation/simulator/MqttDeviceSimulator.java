package com.scentstation.simulator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MqttDeviceSimulator implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(MqttDeviceSimulator.class);
    private final SimulatorConfig config;
    private final ObjectMapper mapper;
    private final DeviceStateStore store;
    private final SimulatorDeviceState state;
    private final CommandProcessor processor;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(3);
    private final ScheduledExecutorService commandExecutor = Executors.newSingleThreadScheduledExecutor();
    private final AtomicBoolean connecting = new AtomicBoolean();
    private final AtomicBoolean subscribed = new AtomicBoolean();
    private volatile Mqtt3AsyncClient client;
    private volatile boolean started;

    public MqttDeviceSimulator(SimulatorConfig config, ObjectMapper mapper, DeviceStateStore store,
                               SimulatorDeviceState state) {
        this.config = config;
        this.mapper = mapper;
        this.store = store;
        this.state = state;
        this.processor = new CommandProcessor(mapper, config, state, store, this::publish);
    }

    public void start() {
        if (started) return;
        started = true;
        URI broker = config.brokerUri();
        var builder = MqttClient.builder().useMqttVersion3()
                .identifier("scentstation-device-" + config.machineId() + "-" + UUID.randomUUID())
                .serverHost(broker.getHost())
                .serverPort(broker.getPort() > 0 ? broker.getPort()
                        : ("ssl".equalsIgnoreCase(broker.getScheme()) ? 8883 : 1883))
                .addConnectedListener(context -> onConnected())
                .addDisconnectedListener(context -> {
                    subscribed.set(false);
                    log.warn("Simulator {} disconnected from MQTT broker", config.machineId());
                });
        if ("ssl".equalsIgnoreCase(broker.getScheme())) builder.useSslWithDefaultConfig();
        client = builder.buildAsync();

        scheduler.scheduleWithFixedDelay(this::connectIfNeeded, 0, config.reconnectIntervalMs(), TimeUnit.MILLISECONDS);
        scheduler.scheduleWithFixedDelay(this::sendHeartbeat, 0, config.heartbeatIntervalMs(), TimeUnit.MILLISECONDS);
        scheduler.scheduleWithFixedDelay(this::sendTelemetry, 0, config.telemetryIntervalMs(), TimeUnit.MILLISECONDS);
        scheduler.scheduleWithFixedDelay(processor::retryUnacknowledgedResponses,
                config.reconnectIntervalMs(), config.reconnectIntervalMs(), TimeUnit.MILLISECONDS);
    }

    public CommandProcessor processor() { return processor; }
    public SimulatorDeviceState state() { return state; }
    public boolean isConnected() { return client != null && client.getState().isConnected() && subscribed.get(); }

    public void networkAvailable(boolean available) {
        state.networkAvailable(available);
        if (!available && client != null && client.getState().isConnected()) {
            client.disconnect();
        } else if (available) {
            connectIfNeeded();
        }
    }

    private void connectIfNeeded() {
        Mqtt3AsyncClient current = client;
        if (!state.networkAvailable() || current == null || current.getState().isConnected()
                || !connecting.compareAndSet(false, true)) return;
        current.connectWith().cleanSession(true).keepAlive(30)
                .simpleAuth().username(config.username())
                .password(config.password().getBytes(StandardCharsets.UTF_8)).applySimpleAuth().send()
                .orTimeout(10, TimeUnit.SECONDS)
                .whenComplete((ack, error) -> {
                    connecting.set(false);
                    if (error != null) log.warn("Simulator {} MQTT connection failed: {}",
                            config.machineId(), error.getClass().getSimpleName());
                });
    }

    private void onConnected() {
        subscribed.set(false);
        String commandTopic = "scentstation/" + config.machineId() + "/command";
        client.subscribeWith().topicFilter(commandTopic).qos(MqttQos.AT_LEAST_ONCE)
                .callback(message -> {
                    String json = new String(message.getPayloadAsBytes(), StandardCharsets.UTF_8);
                    commandExecutor.execute(() -> processor.onCommand(json));
                }).send().whenComplete((ack, error) -> {
                    if (error != null) {
                        log.error("Simulator {} could not subscribe to {}", config.machineId(), commandTopic, error);
                        return;
                    }
                    subscribed.set(true);
                    log.info("Simulator {} subscribed to {} at QoS 1", config.machineId(), commandTopic);
                    processor.recoverInterruptedCommands();
                    processor.publishCurrentFaultAlert(null);
                    publish("scentstation/" + config.machineId() + "/event", safeRebootEvent());
                    sendHeartbeat();
                    sendTelemetry();
                });
    }

    private String safeRebootEvent() {
        try { return processor.rebootEvent(); }
        catch (IOException exception) { throw new IllegalStateException("Could not create reboot event", exception); }
    }

    private void sendHeartbeat() {
        if (!isConnected() || !state.networkAvailable()) return;
        try { publish("scentstation/" + config.machineId() + "/heartbeat", processor.heartbeat()); }
        catch (IOException exception) { log.warn("Could not serialize simulator heartbeat", exception); }
    }

    private void sendTelemetry() {
        if (!isConnected() || !state.networkAvailable()) return;
        try { publish("scentstation/" + config.machineId() + "/telemetry", processor.telemetry()); }
        catch (IOException exception) { log.warn("Could not serialize simulator telemetry", exception); }
    }

    private CompletableFuture<Void> publish(String topic, String json) {
        Mqtt3AsyncClient current = client;
        if (current == null || !current.getState().isConnected() || !subscribed.get()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Simulator is not connected and subscribed"));
        }
        return current.publishWith().topic(topic).qos(MqttQos.AT_LEAST_ONCE).retain(false)
                .payload(json.getBytes(StandardCharsets.UTF_8)).send().thenApply(ack -> null);
    }

    @Override
    public void close() {
        scheduler.shutdownNow();
        commandExecutor.shutdownNow();
        Mqtt3AsyncClient current = client;
        if (current != null && current.getState().isConnected()) {
            try { current.disconnect().get(5, TimeUnit.SECONDS); }
            catch (Exception exception) { log.debug("Simulator disconnect did not complete cleanly", exception); }
        }
    }

    public static ObjectMapper newObjectMapper() {
        return new ObjectMapper().registerModule(new JavaTimeModule());
    }
}
