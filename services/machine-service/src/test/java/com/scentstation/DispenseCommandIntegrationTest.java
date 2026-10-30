package com.scentstation;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DispenseCommandIntegrationTest extends MachineServiceIntegrationTestBase {

    @Test
    void rejectsMissingInternalKeyAndConflictingIdempotencyPayload() {
        String tenantId = uniqueId("tenant");
        Long machineId = createMachine(tenantId);
        patchJson("/api/machines/" + machineId + "/operational-status", Map.of("status", "ONLINE"));
        ResponseEntity<Map<String, Object>> slotResponse = postJson("/api/machines/" + machineId + "/slots",
            slotRequest(1, "SLOT-SEC", "product-sec", "cartridge-sec", true, 1000));
        Long slotId = ((Number) requireBody(slotResponse).get("id")).longValue();
        patchJson("/api/machines/" + machineId + "/slots/" + slotId + "/status?status=AVAILABLE", Map.of());

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("tenantId", tenantId);
        request.put("orderId", uniqueId("order"));
        request.put("paymentReference", uniqueId("payment"));
        request.put("machineId", String.valueOf(machineId));
        request.put("slotId", String.valueOf(slotId));
        request.put("commandId", uniqueId("command"));

        ResponseEntity<Map<String, Object>> unauthorized = rest.exchange("/api/machines/dispense-commands",
            HttpMethod.POST, new HttpEntity<>(request), new org.springframework.core.ParameterizedTypeReference<>() {});
        assertEquals(HttpStatus.UNAUTHORIZED, unauthorized.getStatusCode());

        ResponseEntity<Map<String, Object>> accepted = postJson("/api/machines/dispense-commands", request);
        assertEquals(HttpStatus.ACCEPTED, accepted.getStatusCode());
        Map<String, Object> conflictRequest = new LinkedHashMap<>(request);
        conflictRequest.put("paymentReference", uniqueId("different-payment"));
        ResponseEntity<Map<String, Object>> conflict = postJson("/api/machines/dispense-commands", conflictRequest);
        assertEquals(HttpStatus.CONFLICT, conflict.getStatusCode());
    }

    @Test
    void acceptsIdempotentlyAndRetrievesDispensingCommand() {
        String tenantId = uniqueId("tenant");
        Long machineId = createMachine(tenantId);

        patchJson("/api/machines/" + machineId + "/operational-status",
            Map.of("status", "ONLINE"));
        ResponseEntity<Map<String, Object>> slotResponse = postJson(
            "/api/machines/" + machineId + "/slots",
            slotRequest(1, "SLOT-01", "product-1", "cartridge-1", true, 1000));
        assertEquals(HttpStatus.CREATED, slotResponse.getStatusCode());
        Long slotId = ((Number) requireBody(slotResponse).get("id")).longValue();
        patchJson("/api/machines/" + machineId + "/slots/" + slotId + "/status?status=AVAILABLE", Map.of());

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("tenantId", tenantId);
        request.put("orderId", uniqueId("order"));
        request.put("paymentReference", uniqueId("payment"));
        request.put("machineId", String.valueOf(machineId));
        request.put("slotId", String.valueOf(slotId));
        request.put("commandId", uniqueId("command"));

        ResponseEntity<Map<String, Object>> accepted = postJson("/api/machines/dispense-commands", request);
        assertEquals(HttpStatus.ACCEPTED, accepted.getStatusCode());
        assertNotNull(requireBody(accepted).get("commandId"));

        ResponseEntity<Map<String, Object>> repeated = postJson("/api/machines/dispense-commands", request);
        assertEquals(HttpStatus.ACCEPTED, repeated.getStatusCode());
        assertEquals(requireBody(accepted).get("commandId"), requireBody(repeated).get("commandId"));

        ResponseEntity<Map<String, Object>> unauthorizedGet = rest.exchange(
            "/api/machines/dispense-commands/" + request.get("commandId"), HttpMethod.GET,
            HttpEntity.EMPTY, new org.springframework.core.ParameterizedTypeReference<>() {});
        assertEquals(HttpStatus.UNAUTHORIZED, unauthorizedGet.getStatusCode());

        ResponseEntity<Map<String, Object>> retrieved = getJson(
            "/api/machines/dispense-commands/" + request.get("commandId") + "?tenantId=" + tenantId);
        assertEquals(HttpStatus.OK, retrieved.getStatusCode());
        assertEquals("ACCEPTED", requireBody(retrieved).get("status"));
    }
}
