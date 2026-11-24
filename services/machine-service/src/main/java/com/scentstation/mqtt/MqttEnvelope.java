package com.scentstation.mqtt;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

public record MqttEnvelope(
    int schemaVersion,
    String messageId,
    String machineId,
    String type,
    Instant timestamp,
    JsonNode payload
) {
}
