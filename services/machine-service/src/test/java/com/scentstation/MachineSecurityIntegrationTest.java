package com.scentstation;

import com.scentstation.entity.MachineAlert;
import com.scentstation.entity.MachineAlertSeverity;
import com.scentstation.entity.MachineAlertSource;
import com.scentstation.entity.MachineAlertType;
import com.scentstation.integration.identity.dto.IdentityPrincipal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

class MachineSecurityIntegrationTest extends MachineServiceIntegrationTestBase {
    @Test
    void missingBearerIsUnauthorized() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Test-No-Authorization", "true");
        assertEquals(HttpStatus.UNAUTHORIZED, exchange("/api/machines", HttpMethod.GET,
            headers, null).getStatusCode());
    }

    @Test
    void invalidBearerIsUnauthorized() {
        when(identity.introspect("invalid")).thenThrow(
            new ResponseStatusException(HttpStatus.UNAUTHORIZED, "revoked"));
        assertEquals(HttpStatus.UNAUTHORIZED, get("/api/machines", "invalid").getStatusCode());
    }

    @Test
    void identityOutageFailsClosed() {
        when(identity.introspect("outage")).thenThrow(
            new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "unavailable"));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, get("/api/machines", "outage").getStatusCode());
    }

    @Test
    void spoofedTenantHeadersDoNotGrantAccess() {
        Long machineId = createMachine("1");
        stub("none", principal(1, Set.of(), Set.of(), Set.of(), "ADMIN"));
        HttpHeaders headers = bearer("none");
        headers.set("X-Tenant-Id", "1");
        headers.set("X-Verified-Tenant-Id", "1");
        assertEquals(HttpStatus.FORBIDDEN, exchange("/api/machines/" + machineId,
            HttpMethod.GET, headers, null).getStatusCode());
    }

    @Test
    void machineReadPermissionIsRequired() {
        Long machineId = createMachine("1");
        stub("control", principal(1, Set.of("MACHINE_CONTROL"), Set.of(), Set.of(), "ADMIN"));
        assertEquals(HttpStatus.FORBIDDEN, get("/api/machines/" + machineId, "control").getStatusCode());
    }

    @Test
    void machineControlPermissionIsRequired() {
        Long machineId = createMachine("1");
        stub("read", principal(1, Set.of("MACHINE_READ"), Set.of(), Set.of(), "ADMIN"));
        assertEquals(HttpStatus.FORBIDDEN, exchange("/api/machines/" + machineId
            + "/operational-status", HttpMethod.PATCH, bearer("read"), Map.of("status", "ONLINE"))
            .getStatusCode());
    }

    @Test
    void machineConfigurePermissionIsRequired() {
        Long machineId = createMachine("1");
        stub("read", principal(1, Set.of("MACHINE_READ"), Set.of(), Set.of(), "ADMIN"));
        assertEquals(HttpStatus.FORBIDDEN, exchange("/api/machines/" + machineId
            + "/device-registration-status", HttpMethod.PATCH, bearer("read"), Map.of("status", "ACTIVE"))
            .getStatusCode());
    }

    @Test
    void deviceRevocationRequiresDedicatedPermission() {
        Long machineId = createMachine("1");
        stub("configure", principal(1, Set.of("MACHINE_CONFIGURE"), Set.of(), Set.of(), "ADMIN"));
        assertEquals(HttpStatus.FORBIDDEN, exchange("/api/machines/" + machineId
            + "/device-registration-status", HttpMethod.PATCH, bearer("configure"),
            Map.of("status", "REVOKED")).getStatusCode());
    }

    @Test
    void platformActorWithDeviceRevocationPermissionCanRevoke() {
        Long machineId = createMachine("1");
        stub("platform-revoke", new IdentityPrincipal(303L, "platform", null,
            Set.of("PLATFORM_SUPER_ADMIN"), Set.of("DEVICE_ACCESS_REVOKE"), Set.of(), Set.of()));
        assertEquals(HttpStatus.OK, exchange("/api/machines/" + machineId
            + "/device-registration-status", HttpMethod.PATCH, bearer("platform-revoke"),
            Map.of("status", "REVOKED")).getStatusCode());
    }

    @Test
    void alertReadPermissionIsRequired() {
        MachineAlert alert = alert();
        stub("machine-read", principal(1, Set.of("MACHINE_READ"), Set.of(), Set.of(), "ADMIN"));
        assertEquals(HttpStatus.FORBIDDEN, get("/api/alerts/" + alert.getId() + "?tenantId=1",
            "machine-read").getStatusCode());
    }

    @Test
    void alertManagePermissionIsRequired() {
        MachineAlert alert = alert();
        stub("alert-read", principal(1, Set.of("ALERT_READ"), Set.of(), Set.of(), "ADMIN"));
        assertEquals(HttpStatus.FORBIDDEN, exchange("/api/alerts/" + alert.getId()
            + "/acknowledge?tenantId=1", HttpMethod.POST, bearer("alert-read"), Map.of())
            .getStatusCode());
    }

    @Test
    void crossTenantMachineReadIsBlocked() {
        Long machineId = createMachine("1");
        stub("tenant-2", principal(2, Set.of("MACHINE_READ"), Set.of(), Set.of(), "ADMIN"));
        assertEquals(HttpStatus.FORBIDDEN, get("/api/machines/" + machineId, "tenant-2").getStatusCode());
    }

    @Test
    void crossTenantMachineMutationIsBlocked() {
        Long machineId = createMachine("1");
        stub("tenant-2", principal(2, Set.of("MACHINE_CONTROL"), Set.of(), Set.of(), "ADMIN"));
        assertEquals(HttpStatus.FORBIDDEN, exchange("/api/machines/" + machineId
            + "/operational-status", HttpMethod.PATCH, bearer("tenant-2"), Map.of("status", "ONLINE"))
            .getStatusCode());
    }

    @Test
    void technicianUnassignedMachineIsBlocked() {
        Long machineId = createMachine("1");
        stub("tech-none", principal(1, Set.of("MACHINE_READ"), Set.of(), Set.of(), "TECHNICIAN"));
        assertEquals(HttpStatus.FORBIDDEN, get("/api/machines/" + machineId, "tech-none").getStatusCode());
    }

    @Test
    void technicianCannotCreateAnUnassignedMachine() {
        stub("tech-create", principal(1, Set.of("MACHINE_CONFIGURE"), Set.of(), Set.of(), "TECHNICIAN"));
        assertEquals(HttpStatus.FORBIDDEN, exchange("/api/machines", HttpMethod.POST,
            bearer("tech-create"), machineRequest("1", "1")).getStatusCode());
    }

    @Test
    void technicianAssignedMachineIsAllowed() {
        Long machineId = createMachine("1");
        stub("tech-one", principal(1, Set.of("MACHINE_READ"), Set.of(), Set.of(machineId), "TECHNICIAN"));
        assertEquals(HttpStatus.OK, get("/api/machines/" + machineId, "tech-one").getStatusCode());
    }

    @Test
    void technicianListIsFilteredToAssignedMachines() {
        Long allowed = createMachine("1");
        createMachine("1");
        stub("tech-list", principal(1, Set.of("MACHINE_READ"), Set.of(), Set.of(allowed), "TECHNICIAN"));
        ResponseEntity<List<Map<String, Object>>> response = rest.exchange("/api/machines",
            HttpMethod.GET, new HttpEntity<>(bearer("tech-list")), new ParameterizedTypeReference<>() {});
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, requireListBody(response).size());
        assertEquals(allowed.longValue(), ((Number) requireListBody(response).getFirst().get("id")).longValue());
    }

    @Test
    void locationScopeIsEnforcedAndAmbiguousStringIdsFailClosed() {
        Long machineId = createMachine("1");
        stub("location", principal(1, Set.of("MACHINE_READ"), Set.of(99L), Set.of(machineId), "ADMIN"));
        assertEquals(HttpStatus.FORBIDDEN, get("/api/machines/" + machineId, "location").getStatusCode());
    }

    @Test
    void exactNumericLocationScopeAllowsAssignedMachine() {
        Long machineId = createMachineAt("1", "99");
        stub("location-99", principal(1, Set.of("MACHINE_READ"), Set.of(99L),
            Set.of(machineId), "TECHNICIAN"));
        assertEquals(HttpStatus.OK, get("/api/machines/" + machineId, "location-99").getStatusCode());
    }

    @Test
    void leadingZeroLocationDoesNotAliasNumericScope() {
        Long machineId = createMachineAt("1", "099");
        stub("location-99", principal(1, Set.of("MACHINE_READ"), Set.of(99L),
            Set.of(machineId), "TECHNICIAN"));
        assertEquals(HttpStatus.FORBIDDEN,
            get("/api/machines/" + machineId, "location-99").getStatusCode());
    }

    @Test
    void malformedTenantAndMachineIdentifiersAreRejected() {
        stub("configure", principal(1, Set.of("MACHINE_CONFIGURE"), Set.of(), Set.of(), "ADMIN"));
        assertEquals(HttpStatus.BAD_REQUEST, exchange("/api/machines", HttpMethod.POST,
            bearer("configure"), machineRequest("001", "1")).getStatusCode());

        stub("read", principal(1, Set.of("MACHINE_READ"), Set.of(), Set.of(), "ADMIN"));
        assertEquals(HttpStatus.BAD_REQUEST, get("/api/machines/-1", "read").getStatusCode());
    }

    @Test
    void malformedStoredMachineTenantFailsAsDataIntegrityError() {
        Long machineId = createMachine("1");
        var machine = machineRepository.findById(machineId).orElseThrow();
        machine.setTenantId("TENANT-1");
        machineRepository.saveAndFlush(machine);
        stub("read", principal(1, Set.of("MACHINE_READ"), Set.of(), Set.of(), "ADMIN"));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR,
            get("/api/machines/" + machineId, "read").getStatusCode());
    }

    @Test
    void requestTenantCannotOverridePrincipalTenant() {
        stub("tenant-1", principal(1, Set.of("MACHINE_READ"), Set.of(), Set.of(), "ADMIN"));
        assertEquals(HttpStatus.FORBIDDEN, get("/api/machines?tenantId=2", "tenant-1").getStatusCode());
    }

    private MachineAlert alert() {
        Long machineId = createMachine("1");
        return alertRepository.save(new MachineAlert("security-" + machineId,
            MachineAlertType.MACHINE_OFFLINE, MachineAlertSeverity.HIGH, MachineAlertSource.MONITOR,
            machineRepository.findById(machineId).orElseThrow(), null, "Offline", Instant.now()));
    }

    private Long createMachineAt(String tenantId, String locationId) {
        assertEquals(HttpStatus.CREATED, createLocation(locationId, tenantId).getStatusCode());
        ResponseEntity<Map<String, Object>> response = postJson("/api/machines",
            machineRequest(tenantId, locationId));
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        return ((Number) requireBody(response).get("id")).longValue();
    }

    private IdentityPrincipal principal(long tenantId, Set<String> grants, Set<Long> locations,
                                        Set<Long> machines, String role) {
        return new IdentityPrincipal(202L, "security-user", tenantId, Set.of(role), grants,
            locations, machines);
    }

    private void stub(String token, IdentityPrincipal principal) {
        when(identity.introspect(token)).thenReturn(principal);
    }

    private HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private ResponseEntity<Map<String, Object>> get(String path, String token) {
        return exchange(path, HttpMethod.GET, bearer(token), null);
    }

    private ResponseEntity<Map<String, Object>> exchange(String path, HttpMethod method,
                                                          HttpHeaders headers, Object body) {
        return rest.exchange(path, method, new HttpEntity<>(body, headers),
            new ParameterizedTypeReference<>() {});
    }
}
