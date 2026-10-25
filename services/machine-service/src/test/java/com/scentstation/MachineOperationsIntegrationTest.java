package com.scentstation;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class MachineOperationsIntegrationTest extends MachineServiceIntegrationTestBase {
    @Test
    void disabledMachineMustUseControlEndpointToReEnable() {
        Long machineId = createMachine(uniqueId("tenant"));
        assertEquals(HttpStatus.OK, patchJson(
            "/api/machines/" + machineId + "/operational-status",
            Map.of("status", "DISABLED")
        ).getStatusCode());

        ResponseEntity<Map<String, Object>> bypass = patchJson(
            "/api/machines/" + machineId + "/operational-status",
            Map.of("status", "ONLINE")
        );
        assertEquals(HttpStatus.BAD_REQUEST, bypass.getStatusCode());

        ResponseEntity<Map<String, Object>> enabled = patchJson(
            "/api/machines/" + machineId + "/control",
            Map.of("action", "ENABLE")
        );
        assertEquals(HttpStatus.OK, enabled.getStatusCode());
        assertEquals("OFFLINE", enabled.getBody().get("operationalStatus"));
    }

    @Test
    void operatingHoursCanBeClearedToAllowAllDayAvailability() {
        Long machineId = createMachine(uniqueId("tenant"));
        ResponseEntity<Map<String, Object>> configured = putJson(
            "/api/machines/" + machineId + "/operating-hours",
            Map.of("startTime", "08:00:00", "endTime", "17:00:00")
        );
        assertEquals(HttpStatus.OK, configured.getStatusCode());

        Map<String, Object> clearRequest = new LinkedHashMap<>();
        clearRequest.put("startTime", null);
        clearRequest.put("endTime", null);
        ResponseEntity<Map<String, Object>> cleared = putJson(
            "/api/machines/" + machineId + "/operating-hours",
            clearRequest
        );

        assertEquals(HttpStatus.OK, cleared.getStatusCode());
        assertNull(cleared.getBody().get("operatingHoursStart"));
        assertNull(cleared.getBody().get("operatingHoursEnd"));
    }

    @Test
    void heartbeatUpdatesLastSeenAndBringsOfflineMachineOnline() {
        Long machineId = createMachine(uniqueId("tenant"));

        ResponseEntity<Map<String, Object>> heartbeat =
            postJson("/api/machines/" + machineId + "/heartbeat", Map.of());

        assertEquals(HttpStatus.OK, heartbeat.getStatusCode());
        assertNotNull(heartbeat.getBody().get("lastSeenAt"));
        assertEquals("ONLINE", heartbeat.getBody().get("operationalStatus"));
    }
}
