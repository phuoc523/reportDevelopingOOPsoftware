package com.scentstation.service;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class MqttDeviceTopic {
    private final String deviceIdentity;
    private final String messageType;
    private final String commandId;

    private MqttDeviceTopic(String deviceIdentity, String messageType, String commandId) {
        this.deviceIdentity = deviceIdentity;
        this.messageType = messageType;
        this.commandId = commandId;
    }

    public static MqttDeviceTopic parse(String topic) {
        if (topic == null) {
            throw new IllegalArgumentException("MQTT topic is required");
        }
        String[] segments = topic.split("/", -1);
        if (segments.length == 5 && isDeviceTopicPrefix(segments)
                && isDeviceMessageType(segments[4])) {
            return new MqttDeviceTopic(decodeIdentity(segments[3]), segments[4], null);
        }
        if (segments.length == 7 && isDeviceTopicPrefix(segments)
                && segments[4].equals("commands")
                && !segments[5].isBlank()
                && (segments[6].equals("ack") || segments[6].equals("result"))) {
            return new MqttDeviceTopic(decodeIdentity(segments[3]), segments[6], segments[5]);
        }
        throw new IllegalArgumentException("Unsupported machine MQTT topic");
    }

    public static String encodeIdentity(String identity) {
        if (identity == null || identity.isBlank() || identity.length() > 150) {
            throw new IllegalArgumentException("Device identity must contain between 1 and 150 characters");
        }
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(identity.getBytes(StandardCharsets.UTF_8));
    }

    private static String decodeIdentity(String token) {
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(token);
            String identity = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(decoded))
                .toString();
            if (identity.isBlank() || identity.length() > 150
                    || !encodeIdentity(identity).equals(token)) {
                throw new IllegalArgumentException("Device identity token in MQTT topic is invalid");
            }
            return identity;
        } catch (IllegalArgumentException | CharacterCodingException exception) {
            throw new IllegalArgumentException("Device identity token in MQTT topic is invalid", exception);
        }
    }

    private static boolean isDeviceTopicPrefix(String[] segments) {
        return segments[0].equals("scentstation")
            && segments[1].equals("v1")
            && segments[2].equals("devices");
    }

    private static boolean isDeviceMessageType(String type) {
        return type.equals("heartbeat") || type.equals("telemetry") || type.equals("events");
    }

    public String getDeviceIdentity() { return deviceIdentity; }
    public String getMessageType() { return messageType; }
    public String getCommandId() { return commandId; }
}
