package com.scentstation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.scentstation.entity.MachineAlertStatus;
import com.scentstation.entity.MachineAlertType;
import com.scentstation.repository.MachineTelemetryLatestRepository;
import com.scentstation.service.MachineMqttMessageHandler;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
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
