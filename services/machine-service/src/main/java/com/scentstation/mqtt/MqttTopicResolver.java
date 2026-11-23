package com.scentstation.mqtt;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MqttTopicResolver {
    private static final Pattern DEVICE_TOPIC = Pattern.compile(
        "^scentstation/v1/devices/([A-Za-z0-9._-]{1,150})/(heartbeat|telemetry|events|acks|results)$");

    public static final String DEVICE_INBOUND_FILTER =
        "scentstation/v1/devices/+/heartbeat,scentstation/v1/devices/+/telemetry,"
            + "scentstation/v1/devices/+/events,scentstation/v1/devices/+/acks,"
            + "scentstation/v1/devices/+/results";
    public static final String DEVICE_TEST_TOPIC = "scentstation/v1/devices/%s/backend/test";

    private MqttTopicResolver() {
    }

    public static DeviceTopic parseDeviceTopic(String topic) {
        if (topic == null) {
            throw new InvalidMqttMessageException("MQTT topic is required");
        }
        Matcher matcher = DEVICE_TOPIC.matcher(topic);
        if (!matcher.matches()) {
            throw new InvalidMqttMessageException("MQTT topic is not an allowed device topic");
        }
        return new DeviceTopic(matcher.group(1), matcher.group(2));
    }

    public static String deviceTestTopic(String deviceIdentity) {
        if (deviceIdentity == null || !deviceIdentity.matches("[A-Za-z0-9._-]{1,150}")) {
            throw new IllegalArgumentException("Device identity is not a valid MQTT topic segment");
        }
        return DEVICE_TEST_TOPIC.formatted(deviceIdentity);
    }

    public static String expectedType(String channel) {
        return switch (channel) {
            case "heartbeat" -> "HEARTBEAT";
            case "telemetry" -> "TELEMETRY";
            case "events" -> "DEVICE_EVENT";
            case "acks" -> "COMMAND_ACK";
            case "results" -> "DISPENSE_RESULT";
            default -> throw new InvalidMqttMessageException("MQTT topic channel is not supported");
        };
    }

    public record DeviceTopic(String deviceIdentity, String channel) {
    }
}
