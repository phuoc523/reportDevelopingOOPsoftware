package com.scentstation.mqtt;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

public record DeviceMessageEnvelope(
    int schemaVersion,
    String messageId,
    String deviceId,
    String type,
    Instant timestamp,
    JsonNode payload
) {
}
