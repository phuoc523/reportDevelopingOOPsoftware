package com.scentstation;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.core.ParameterizedTypeReference;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.datasource.url=jdbc:h2:mem:machine-service-test;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
    }
)
abstract class MachineServiceIntegrationTestBase {
    @Autowired
    protected TestRestTemplate rest;

    @Autowired
    private MachineHistoryRepository historyRepository;

    @Autowired
    private MachineSlotRepository slotRepository;

    @Autowired
    protected MachineRepository machineRepository;

    @Autowired
    private LocationRepository locationRepository;

    @BeforeEach
    void clearDatabase() {
        historyRepository.deleteAllInBatch();
        slotRepository.deleteAllInBatch();
        machineRepository.deleteAllInBatch();
        locationRepository.deleteAllInBatch();
    }

    protected String uniqueId(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    protected Map<String, Object> locationRequest(String locationId, String tenantId) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("locationId", locationId);
        request.put("tenantId", tenantId);
        request.put("name", "Test location");
        request.put("address", "Test address");
        return request;
    }

    protected ResponseEntity<Map<String, Object>> postJson(String path, Object request) {
        return rest.exchange(
            path,
            HttpMethod.POST,
            new HttpEntity<>(request),
            new ParameterizedTypeReference<>() {
            }
        );
    }

    protected ResponseEntity<Map<String, Object>> getJson(String path) {
        return rest.exchange(
            path,
            HttpMethod.GET,
            HttpEntity.EMPTY,
            new ParameterizedTypeReference<>() {
            }
        );
    }

    protected ResponseEntity<List<Map<String, Object>>> getJsonList(String path) {
        return rest.exchange(
            path,
            HttpMethod.GET,
            HttpEntity.EMPTY,
            new ParameterizedTypeReference<>() {
            }
        );
    }

    protected ResponseEntity<Map<String, Object>> putJson(String path, Object request) {
        return rest.exchange(
            path,
            HttpMethod.PUT,
            new HttpEntity<>(request),
            new ParameterizedTypeReference<>() {
            }
        );
    }

    protected ResponseEntity<Map<String, Object>> createLocation(String locationId, String tenantId) {
        return postJson("/api/locations", locationRequest(locationId, tenantId));
    }

    protected ResponseEntity<Map<String, Object>> patchJson(String path, Map<String, ?> request) {
        return rest.exchange(
            path,
            HttpMethod.PATCH,
            new HttpEntity<>(request),
            new ParameterizedTypeReference<>() {
            }
        );
    }

    protected Map<String, Object> machineRequest(String tenantId, String locationId) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("serialNumber", uniqueId("serial"));
        request.put("deviceIdentity", uniqueId("device"));
        request.put("tenantId", tenantId);
        request.put("locationId", locationId);
        request.put("name", "Test machine");
        return request;
    }

    protected Map<String, Object> slotRequest(int slotNumber, String slotCode,
                                              String productId, String cartridgeId,
                                              boolean enabled, int durationMs) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("slotNumber", slotNumber);
        request.put("slotCode", slotCode);
        request.put("productId", productId);
        request.put("cartridgeId", cartridgeId);
        request.put("enabled", enabled);
        request.put("stockThreshold", 2);
        request.put("calibratedSprayDurationMs", durationMs);
        request.put("doseMl", 0.5);
        request.put("price", "12.50");
        return request;
    }

    protected Long createMachine(String tenantId) {
        String locationId = uniqueId("location");
        ResponseEntity<Map<String, Object>> locationResponse = createLocation(locationId, tenantId);
        assertEquals(HttpStatus.CREATED, locationResponse.getStatusCode());

        ResponseEntity<Map<String, Object>> machineResponse =
            postJson("/api/machines", machineRequest(tenantId, locationId));
        assertEquals(HttpStatus.CREATED, machineResponse.getStatusCode());
        assertNotNull(machineResponse.getBody());
        return ((Number) machineResponse.getBody().get("id")).longValue();
    }
}
