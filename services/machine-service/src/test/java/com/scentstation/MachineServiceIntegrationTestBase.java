package com.scentstation;

import com.scentstation.repository.AlertHandoffOutboxRepository;
import com.scentstation.repository.LocationRepository;
import com.scentstation.repository.MachineAlertRepository;
import com.scentstation.repository.MachineCommandRepository;
import com.scentstation.repository.MachineHistoryRepository;
import com.scentstation.repository.MachineRepository;
import com.scentstation.repository.MachineSlotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.lang.NonNull;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
abstract class MachineServiceIntegrationTestBase {
    @Autowired
    protected TestRestTemplate rest;

    @Autowired
    private MachineHistoryRepository historyRepository;

    @Autowired
    private MachineAlertRepository alertRepository;

    @Autowired
    private MachineCommandRepository commandRepository;

    @Autowired
    private MachineSlotRepository slotRepository;

    @Autowired
    protected MachineRepository machineRepository;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private AlertHandoffOutboxRepository handoffOutboxRepository;

    @BeforeEach
    void clearDatabase() {
        handoffOutboxRepository.deleteAllInBatch();
        commandRepository.deleteAllInBatch();
        alertRepository.deleteAllInBatch();
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
        HttpHeaders headers = new HttpHeaders();
        if (path.contains("dispense-commands")) {
            headers.set("X-Internal-Service-Key", "test-integration-secret");
        }
        return rest.exchange(
            path,
            HttpMethod.POST,
            new HttpEntity<>(Objects.requireNonNull(request, "request"), headers),
            new ParameterizedTypeReference<>() {
            }
        );
    }

    protected ResponseEntity<Map<String, Object>> getJson(String path) {
        HttpHeaders headers = new HttpHeaders();
        if (path.contains("dispense-commands")) {
            headers.set("X-Internal-Service-Key", "test-integration-secret");
        }
        return rest.exchange(
            path,
            HttpMethod.GET,
            new HttpEntity<>(headers),
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
            new HttpEntity<>(Objects.requireNonNull(request, "request")),
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
            new HttpEntity<>(Objects.requireNonNull(request, "request")),
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

    @NonNull
    protected Long createMachine(String tenantId) {
        String locationId = uniqueId("location");
        ResponseEntity<Map<String, Object>> locationResponse = createLocation(locationId, tenantId);
        assertEquals(HttpStatus.CREATED, locationResponse.getStatusCode());

        ResponseEntity<Map<String, Object>> machineResponse =
            postJson("/api/machines", machineRequest(tenantId, locationId));
        assertEquals(HttpStatus.CREATED, machineResponse.getStatusCode());
        return ((Number) requireBody(machineResponse).get("id")).longValue();
    }

    protected Map<String, Object> requireBody(ResponseEntity<Map<String, Object>> response) {
        return Objects.requireNonNull(response.getBody(), "Expected response body");
    }

    protected List<Map<String, Object>> requireListBody(
            ResponseEntity<List<Map<String, Object>>> response) {
        return Objects.requireNonNull(response.getBody(), "Expected response body");
    }
}
