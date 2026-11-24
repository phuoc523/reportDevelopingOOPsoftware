package com.scentstation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.scentstation.entity.MachineAlertSource;
import com.scentstation.entity.MachineAlertStatus;
import com.scentstation.entity.MachineAlertType;
import com.scentstation.repository.MachineDeviceEventRepository;
import com.scentstation.repository.MachineTelemetryLatestRepository;
import com.scentstation.service.MachineMqttMessageHandler;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

class MachineTelemetryIntegrationTest extends MachineServiceIntegrationTestBase {
    @Autowired
    private MachineMqttMessageHandler mqttMessages;

    @Autowired
    private MachineTelemetryLatestRepository telemetry;

    @Autowired
    private MachineDeviceEventRepository deviceEvents;

    @Test
    void lowLevelCreatesOneAlertAndRecoveryResolvesIt() {
        Long machineId = createMachine("1");
        Map<String, Object> slotRequest = slotRequest(1, "SLOT-LEVEL", "product-1",
            "cartridge-level", true, 1000);
        slotRequest.put("stockThreshold", 10);
        var slotResponse = postJson("/api/machines/" + machineId + "/slots", slotRequest);
        assertEquals(HttpStatus.CREATED, slotResponse.getStatusCode());
        Long slotId = ((Number) requireBody(slotResponse).get("id")).longValue();

        sendTelemetry(machineId, slotId, "5.5", Instant.now().minusSeconds(1));
        sendTelemetry(machineId, slotId, "4.0", Instant.now());

        var snapshot = telemetry.findByMachineIdAndSlotId(machineId, slotId).orElseThrow();
        assertThat(snapshot.getRemainingPercent()).isEqualByComparingTo("4.0");
        var alerts = alertRepository.findAll().stream()
            .filter(alert -> alert.getType() == MachineAlertType.LOW_STOCK)
            .toList();
        assertThat(alerts).hasSize(1);
        assertThat(alerts.getFirst().getStatus()).isEqualTo(MachineAlertStatus.OPEN);

        sendTelemetry(machineId, slotId, "50", Instant.now().plusMillis(1));

        assertThat(alertRepository.findById(alerts.getFirst().getId()).orElseThrow().getStatus())
            .isEqualTo(MachineAlertStatus.RESOLVED);

        var telemetryResponse = getJsonList("/api/machines/" + machineId + "/telemetry");
        assertEquals(HttpStatus.OK, telemetryResponse.getStatusCode());
        assertThat(telemetryResponse.getBody()).hasSize(1);
        assertThat(((Number) telemetryResponse.getBody().getFirst().get("remainingPercent")).doubleValue())
            .isEqualTo(50.0);
    }

    @Test
    void handlesDeviceAlertAndDeviceEventEndToEnd() {
        Long machineId = createMachine("1");
        Map<String, Object> slotRequest = slotRequest(1, "SLOT-ALERT", "product-1",
            "cartridge-alert", true, 1000);
        var slotResponse = postJson("/api/machines/" + machineId + "/slots", slotRequest);
        assertEquals(HttpStatus.CREATED, slotResponse.getStatusCode());
        Long slotId = ((Number) requireBody(slotResponse).get("id")).longValue();

        Instant now = Instant.now();
        String alertJson = """
            {"schemaVersion":1,"messageId":"%s","machineId":"%d","type":"DEVICE_ALERT",
             "timestamp":"%s","payload":{"alertType":"LEAK_DETECTED","severity":"CRITICAL",
             "slotId":"%d","summary":"Leak detected on slot"}}
            """.formatted(UUID.randomUUID(), machineId, now, slotId);
        mqttMessages.handle("scentstation/" + machineId + "/alert",
            alertJson.getBytes(StandardCharsets.UTF_8));

        var leakAlerts = alertRepository.findAll().stream()
            .filter(alert -> alert.getType() == MachineAlertType.LEAK_DETECTED
                && alert.getMachineId().equals(machineId))
            .toList();
        assertThat(leakAlerts).hasSize(1);
        assertThat(leakAlerts.getFirst().getSource()).isEqualTo(MachineAlertSource.DEVICE);
        assertThat(leakAlerts.getFirst().getSlotId()).isEqualTo(slotId);

        String eventJson = """
            {"schemaVersion":1,"messageId":"%s","machineId":"%d","type":"DEVICE_EVENT",
             "timestamp":"%s","payload":{"eventType":"DEVICE_REBOOTED",
             "details":"ESP32 booted and connected to MQTT"}}
            """.formatted(UUID.randomUUID(), machineId, now);
        mqttMessages.handle("scentstation/" + machineId + "/event",
            eventJson.getBytes(StandardCharsets.UTF_8));

        var events = deviceEvents.findAllByMachineIdOrderByOccurredAtDesc(machineId);
        assertThat(events).hasSize(1);
        assertThat(events.getFirst().getEventType()).isEqualTo("DEVICE_REBOOTED");
    }

    private void sendTelemetry(Long machineId, Long slotId, String remainingPercent, Instant timestamp) {
        String json = """
            {"schemaVersion":1,"messageId":"%s","machineId":"%d","type":"TELEMETRY",
             "timestamp":"%s","payload":{"slots":[{"slotId":"%d","remainingPercent":%s,
             "sensorStatus":"NORMAL"}]}}
            """.formatted(UUID.randomUUID(), machineId, timestamp, slotId, remainingPercent);
        mqttMessages.handle("scentstation/" + machineId + "/telemetry",
            json.getBytes(StandardCharsets.UTF_8));
    }
}
