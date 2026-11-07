package com.scentstation;

import com.scentstation.entity.MachineAlertStatus;
import com.scentstation.service.MachineAlertEscalationJob;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AlertEscalationIntegrationTest extends MachineServiceIntegrationTestBase {
    @Autowired
    private MachineAlertEscalationJob escalationJob;

    @Test
    void scheduledEscalationMovesOverdueLowStockAlertToEscalated() throws InterruptedException {
        String tenantId = uniqueId("tenant");
        Long machineId = createMachine(tenantId);
        ResponseEntity<Map<String, Object>> slotResponse = postJson(
            "/api/machines/" + machineId + "/slots",
            slotRequest(1, "ALERT-SLOT", "product-alert", "cartridge-alert", true, 1000));
        Long slotId = ((Number) requireBody(slotResponse).get("id")).longValue();

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("tenantId", tenantId);
        request.put("machineId", String.valueOf(machineId));
        request.put("slotId", String.valueOf(slotId));
        request.put("eventId", uniqueId("stock-event"));
        request.put("summary", "Low stock integration test");

        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.set("X-Internal-Service-Key", "test-integration-secret");
        ResponseEntity<Map<String, Object>> created = rest.exchange("/api/alerts/integration/low-stock",
            org.springframework.http.HttpMethod.POST, new org.springframework.http.HttpEntity<>(request, headers),
            new org.springframework.core.ParameterizedTypeReference<>() {});
        assertEquals(HttpStatus.CREATED, created.getStatusCode());

        Thread.sleep(20);
        escalationJob.escalateOverdueAlerts();
        ResponseEntity<Map<String, Object>> escalated = getJson("/api/alerts/" + requireBody(created).get("id") + "?tenantId=" + tenantId);
        assertEquals(MachineAlertStatus.ESCALATED.name(), requireBody(escalated).get("status"));
    }
}
