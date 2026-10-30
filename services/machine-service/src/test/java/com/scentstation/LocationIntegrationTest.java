package com.scentstation;

import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LocationIntegrationTest extends MachineServiceIntegrationTestBase {
    @Test
    void createsListsAndUpdatesLocationWithoutChangingTenantOwnership() {
        String tenantId = uniqueId("tenant");
        String locationId = uniqueId("location");
        assertEquals(HttpStatus.CREATED, createLocation(locationId, tenantId).getStatusCode());

        ResponseEntity<List<Map<String, Object>>> locations =
            getJsonList("/api/locations?tenantId=" + tenantId);
        assertEquals(HttpStatus.OK, locations.getStatusCode());
        assertEquals(1, requireListBody(locations).size());

        Map<String, Object> update = locationRequest(locationId, tenantId);
        update.put("name", "Updated location");
        ResponseEntity<Map<String, Object>> updated = putJson("/api/locations/" + locationId, update);
        assertEquals(HttpStatus.OK, updated.getStatusCode());
        assertEquals("Updated location", requireBody(updated).get("name"));

        update.put("tenantId", uniqueId("other-tenant"));
        ResponseEntity<Map<String, Object>> rejectedUpdate =
            putJson("/api/locations/" + locationId, update);
        assertEquals(HttpStatus.BAD_REQUEST, rejectedUpdate.getStatusCode());
    }

    @Test
    void getsAndDeactivatesLocation() {
        String tenantId = uniqueId("tenant");
        String locationId = uniqueId("location");
        assertEquals(HttpStatus.CREATED, createLocation(locationId, tenantId).getStatusCode());

        ResponseEntity<Map<String, Object>> location = getJson("/api/locations/" + locationId);
        assertEquals(HttpStatus.OK, location.getStatusCode());
        assertEquals(tenantId, requireBody(location).get("tenantId"));

        ResponseEntity<Map<String, Object>> deactivated =
            rest.exchange("/api/locations/" + locationId + "/active?active=false",
                HttpMethod.PATCH, HttpEntity.EMPTY,
                new ParameterizedTypeReference<>() {
                });
        assertEquals(HttpStatus.OK, deactivated.getStatusCode());
        assertEquals(Boolean.FALSE, requireBody(deactivated).get("active"));
    }

    @Test
    void inactiveLocationCannotBeUsedToRegisterMachine() {
        String tenantId = uniqueId("tenant");
        String locationId = uniqueId("location");
        assertEquals(HttpStatus.CREATED, createLocation(locationId, tenantId).getStatusCode());

        rest.exchange("/api/locations/" + locationId + "/active?active=false",
            HttpMethod.PATCH, HttpEntity.EMPTY, new ParameterizedTypeReference<>() {
            });

        ResponseEntity<Map<String, Object>> registration =
            postJson("/api/machines", machineRequest(tenantId, locationId));

        assertEquals(HttpStatus.BAD_REQUEST, registration.getStatusCode());
        assertEquals(0, machineRepository.count());
    }
}
