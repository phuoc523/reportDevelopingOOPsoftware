package com.scentstation;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.core.ParameterizedTypeReference;

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
        assertEquals("OFFLINE", requireBody(enabled).get("operationalStatus"));
    }

    @Test
    void maintenanceCannotBeCompletedWithoutChecklistAndPostTest() {
        Long machineId = createMachine(uniqueId("tenant"));
        ResponseEntity<Map<String, Object>> maintenance = patchJson(
            "/api/machines/" + machineId + "/control",
            Map.of("action", "ENTER_MAINTENANCE")
        );
        assertEquals(HttpStatus.OK, maintenance.getStatusCode());

        ResponseEntity<Map<String, Object>> incomplete = postJson(
            "/api/machines/" + machineId + "/maintenance-completion",
            Map.of("checklistItems", java.util.List.of("HARDWARE_INSPECTION"),
                   "postMaintenanceTestPassed", false)
        );
        assertEquals(HttpStatus.BAD_REQUEST, incomplete.getStatusCode());

        ResponseEntity<Map<String, Object>> complete = postJson(
            "/api/machines/" + machineId + "/maintenance-completion",
            Map.of("checklistItems", java.util.List.of("HARDWARE_INSPECTION", "CLEANING", "CALIBRATION_CHECK"),
                   "postMaintenanceTestPassed", true)
        );
        assertEquals(HttpStatus.OK, complete.getStatusCode());

        ResponseEntity<Map<String, Object>> enabled = patchJson(
            "/api/machines/" + machineId + "/control",
            Map.of("action", "ENABLE")
        );
        assertEquals(HttpStatus.OK, enabled.getStatusCode());
        assertEquals("OFFLINE", requireBody(enabled).get("operationalStatus"));
        assertEquals(true, requireBody(enabled).get("maintenanceChecklistComplete"));
        assertEquals(true, requireBody(enabled).get("postMaintenanceTestPassed"));
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
        assertNull(requireBody(cleared).get("operatingHoursStart"));
        assertNull(requireBody(cleared).get("operatingHoursEnd"));
    }

}
