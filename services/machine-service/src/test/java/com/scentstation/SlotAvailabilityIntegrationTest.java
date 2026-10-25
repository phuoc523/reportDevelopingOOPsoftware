package com.scentstation;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SlotAvailabilityIntegrationTest extends MachineServiceIntegrationTestBase {
    @Test
    void configuredSlotBecomesAvailableWhenMachineIsOnline() {
        Long machineId = createMachine(uniqueId("tenant"));

        ResponseEntity<Map<String, Object>> online = patchJson(
            "/api/machines/" + machineId + "/operational-status",
            Map.of("status", "ONLINE")
        );
        assertEquals(HttpStatus.OK, online.getStatusCode());

        Map<String, Object> slotRequest =
            slotRequest(1, "A1", uniqueId("product"), uniqueId("cartridge"), true, 1000);

        ResponseEntity<Map<String, Object>> createdSlot =
            postJson("/api/machines/" + machineId + "/slots", slotRequest);
        assertEquals(HttpStatus.CREATED, createdSlot.getStatusCode());

        ResponseEntity<Map<String, Object>> availability =
            getJson("/api/machines/" + machineId + "/availability");
        assertEquals(HttpStatus.OK, availability.getStatusCode());
        Map<String, Object> availabilityBody = requireBody(availability);
        assertEquals(Boolean.TRUE, availabilityBody.get("machineAvailable"));

        List<?> slots = (List<?>) availabilityBody.get("slots");
        assertEquals(1, slots.size());
        assertEquals(Boolean.TRUE, ((Map<?, ?>) slots.get(0)).get("available"));
    }

    @Test
    void disabledSlotIsNotAvailableEvenWhenMachineIsOnline() {
        Long machineId = createMachine(uniqueId("tenant"));
        patchJson(
            "/api/machines/" + machineId + "/operational-status",
            Map.of("status", "ONLINE")
        );

        Map<String, Object> slotRequest =
            slotRequest(1, "A1", uniqueId("product"), uniqueId("cartridge"), false, 1000);

        ResponseEntity<Map<String, Object>> createdSlot =
            postJson("/api/machines/" + machineId + "/slots", slotRequest);
        assertEquals(HttpStatus.CREATED, createdSlot.getStatusCode());

        ResponseEntity<Map<String, Object>> availability =
            getJson("/api/machines/" + machineId + "/availability");
        List<?> slots = (List<?>) requireBody(availability).get("slots");
        assertEquals(1, slots.size());
        assertFalse((Boolean) ((Map<?, ?>) slots.get(0)).get("available"));
        assertEquals("Slot is disabled", ((Map<?, ?>) slots.get(0)).get("availabilityReason"));
    }

    @Test
    void cartridgeCannotBeAssignedToSlotsOnDifferentMachines() {
        Long firstMachineId = createMachine(uniqueId("tenant"));
        Long secondMachineId = createMachine(uniqueId("tenant"));
        String cartridgeId = uniqueId("cartridge");
        ResponseEntity<Map<String, Object>> firstSlot = postJson(
            "/api/machines/" + firstMachineId + "/slots",
            slotRequest(1, "A1", uniqueId("product"), cartridgeId, true, 1000)
        );
        assertEquals(HttpStatus.CREATED, firstSlot.getStatusCode());

        ResponseEntity<Map<String, Object>> duplicateSlot = postJson(
            "/api/machines/" + secondMachineId + "/slots",
            slotRequest(1, "A1", uniqueId("product"), cartridgeId.toUpperCase(), true, 1000)
        );

        assertEquals(HttpStatus.CONFLICT, duplicateSlot.getStatusCode());
    }

    @Test
    void revokedMachineCannotChangeItsSlots() {
        Long machineId = createMachine(uniqueId("tenant"));
        ResponseEntity<Map<String, Object>> revoked = patchJson(
            "/api/machines/" + machineId + "/device-registration-status",
            Map.of("status", "REVOKED")
        );
        assertEquals(HttpStatus.OK, revoked.getStatusCode());

        ResponseEntity<Map<String, Object>> slotResponse = postJson(
            "/api/machines/" + machineId + "/slots",
            slotRequest(1, "A1", uniqueId("product"), uniqueId("cartridge"), true, 1000)
        );

        assertEquals(HttpStatus.BAD_REQUEST, slotResponse.getStatusCode());
    }

    @Test
    void rejectsSprayDurationAboveFiveSecondsWithFieldSpecificError() {
        Long machineId = createMachine(uniqueId("tenant"));

        ResponseEntity<Map<String, Object>> response = postJson(
            "/api/machines/" + machineId + "/slots",
            slotRequest(1, "A1", null, null, true, 5001)
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(true, requireBody(response).get("detail").toString()
            .contains("calibratedSprayDurationMs"));
    }
}
