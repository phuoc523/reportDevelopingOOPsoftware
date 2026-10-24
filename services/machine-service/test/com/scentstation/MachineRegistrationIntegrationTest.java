package com.scentstation;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class MachineRegistrationIntegrationTest extends MachineServiceIntegrationTestBase {
    @Test
    void registersMachineAndPersistsItsHistory() {
        String tenantId = uniqueId("tenant");
        String locationId = uniqueId("location");
        assertEquals(HttpStatus.CREATED, createLocation(locationId, tenantId).getStatusCode());

        ResponseEntity<Map<String, Object>> registration =
            postJson("/api/machines", machineRequest(tenantId, locationId));

        assertEquals(HttpStatus.CREATED, registration.getStatusCode());
        assertNotNull(registration.getBody());
        Long machineId = ((Number) registration.getBody().get("id")).longValue();

        ResponseEntity<Map<String, Object>> savedMachine = getJson("/api/machines/" + machineId);
        assertEquals(HttpStatus.OK, savedMachine.getStatusCode());
        assertEquals(tenantId, savedMachine.getBody().get("tenantId"));

        ResponseEntity<List<Map<String, Object>>> history =
            getJsonList("/api/machines/" + machineId + "/history");
        assertEquals(HttpStatus.OK, history.getStatusCode());
        assertNotNull(history.getBody());
        assertFalse(history.getBody().isEmpty());
        assertEquals("REGISTERED", history.getBody().get(0).get("action"));
    }

    @Test
    void rejectsRegistrationWhenLocationBelongsToAnotherTenant() {
        String locationTenant = uniqueId("tenant");
        String machineTenant = uniqueId("tenant");
        String locationId = uniqueId("location");
        assertEquals(HttpStatus.CREATED, createLocation(locationId, locationTenant).getStatusCode());

        ResponseEntity<Map<String, Object>> registration =
            postJson("/api/machines", machineRequest(machineTenant, locationId));

        assertEquals(HttpStatus.BAD_REQUEST, registration.getStatusCode());
        assertEquals(0, machineRepository.count());
    }

    @Test
    void rejectsDuplicateSerialNumberRegardlessOfLetterCase() {
        Long existingId = createMachine(uniqueId("tenant"));
        Map<String, Object> existingMachine = getJson("/api/machines/" + existingId).getBody();
        String serialNumber = (String) existingMachine.get("serialNumber");
        String tenantId = uniqueId("tenant");
        String locationId = uniqueId("location");
        assertEquals(HttpStatus.CREATED, createLocation(locationId, tenantId).getStatusCode());

        Map<String, Object> duplicateRequest = machineRequest(tenantId, locationId);
        duplicateRequest.put("serialNumber", serialNumber.toUpperCase(Locale.ROOT));

        ResponseEntity<Map<String, Object>> response = postJson("/api/machines", duplicateRequest);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals(1, machineRepository.count());
    }
}
