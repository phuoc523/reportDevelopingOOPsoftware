package com.scentstation.mqtt;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MqttTopics {
    public static final String[] INBOUND_FILTERS = {
        "scentstation/+/response",
        "scentstation/+/heartbeat",
        "scentstation/+/telemetry",
        "scentstation/+/event",
        "scentstation/+/alert"
    };

    private static final Pattern INBOUND = Pattern.compile(
        "^scentstation/([1-9][0-9]*)/(response|heartbeat|telemetry|event|alert)$");

    private MqttTopics() {
    }

    public static String command(long machineId) {
        if (machineId <= 0) {
            throw new IllegalArgumentException("machineId must be positive");
        }
        return "scentstation/" + machineId + "/command";
    }

    public static ParsedTopic parse(String topic) {
        Matcher matcher = INBOUND.matcher(topic == null ? "" : topic);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Unsupported MQTT topic");
        }
        return new ParsedTopic(Long.parseLong(matcher.group(1)), matcher.group(2));
    }

    public static String expectedType(String channel) {
        return switch (channel) {
            case "response" -> "COMMAND_RESPONSE";
            case "heartbeat" -> "HEARTBEAT";
            case "telemetry" -> "TELEMETRY";
            case "event" -> "DEVICE_EVENT";
            case "alert" -> "DEVICE_ALERT";
            default -> throw new IllegalArgumentException("Unsupported MQTT channel");
        };
    }

    public record ParsedTopic(long machineId, String channel) {
    }
}
