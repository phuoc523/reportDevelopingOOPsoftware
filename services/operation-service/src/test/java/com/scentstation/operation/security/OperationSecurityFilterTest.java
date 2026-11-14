package com.scentstation.operation.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scentstation.operation.integration.identity.client.IdentityIntrospectionClient;
import com.scentstation.operation.integration.identity.dto.IdentityPrincipal;
import com.scentstation.operation.maintenance.dto.response.MaintenanceTicketResponse;
import com.scentstation.operation.maintenance.entity.MaintenanceTicket;
import com.scentstation.operation.maintenance.repository.MaintenanceTicketRepository;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OperationSecurityFilterTest {
    private IdentityIntrospectionClient identity;
    private MaintenanceTicketRepository tickets;
    private OperationAuthorization authorization;
    private OperationSecurityFilter filter;

    @BeforeEach
    void setUp() {
        identity = mock(IdentityIntrospectionClient.class);
        tickets = mock(MaintenanceTicketRepository.class);
        authorization = new OperationAuthorization(tickets);
        filter = new OperationSecurityFilter(identity, authorization, new ObjectMapper(), "server-key");
    }

    @Test
    void missingBearerIsUnauthorized() throws Exception {
        assertEquals(401, execute(request("GET", "/api/inventory/batches"), null).getStatus());
    }

    @Test
    void invalidOrRevokedBearerIsUnauthorized() throws Exception {
        when(identity.introspect("invalid")).thenThrow(status(401));
        assertEquals(401, execute(bearer("GET", "/api/inventory/batches", "invalid"), null).getStatus());
    }

    @Test
    void identityOutageFailsClosed() throws Exception {
        when(identity.introspect("outage")).thenThrow(status(503));
        assertEquals(503, execute(bearer("GET", "/api/inventory/batches", "outage"), null).getStatus());
    }

    @Test
    void malformedIdentityResponseFailsClosed() throws Exception {
        when(identity.introspect("malformed")).thenThrow(status(502));
        assertEquals(502, execute(bearer("GET", "/api/inventory/batches", "malformed"), null).getStatus());
    }

    @Test
    void spoofedTenantAndVerifiedHeadersDoNotAuthenticate() throws Exception {
        MockHttpServletRequest request = request("GET", "/api/inventory/batches");
        request.addHeader("X-Tenant-Id", "1");
        request.addHeader("X-Verified-Tenant-Id", "1");
        assertEquals(401, execute(request, null).getStatus());
    }

    @Test
    void inventoryReadPermissionIsRequired() throws Exception {
        stub("manage", principal(Set.of("INVENTORY_MANAGE"), Set.of(), Set.of(), "ADMIN"));
        assertEquals(403, execute(bearer("GET", "/api/inventory/batches", "manage"), null).getStatus());
    }

    @Test
    void inventoryManagePermissionIsRequired() throws Exception {
        stub("read", principal(Set.of("INVENTORY_READ"), Set.of(), Set.of(), "ADMIN"));
        MockHttpServletRequest request = json("POST", "/api/inventory/batches", "read", "{\"tenantId\":1}");
        assertEquals(403, execute(request, null).getStatus());
    }

    @Test
    void inventoryAdjustRequiresDedicatedPermission() throws Exception {
        stub("manage", principal(Set.of("INVENTORY_MANAGE"), Set.of(), Set.of(), "ADMIN"));
        MockHttpServletRequest request = json("POST", "/api/inventory/adjustments", "manage", "{\"tenantId\":1}");
        assertEquals(403, execute(request, null).getStatus());
    }

    @Test
    void maintenanceReadPermissionIsRequired() throws Exception {
        stub("manage", principal(Set.of("MAINTENANCE_MANAGE"), Set.of(), Set.of(), "ADMIN"));
        assertEquals(403, execute(bearer("GET", "/api/maintenance/tickets", "manage"), null).getStatus());
    }

    @Test
    void maintenanceManagePermissionIsRequired() throws Exception {
        stub("read", principal(Set.of("MAINTENANCE_READ"), Set.of(), Set.of(), "ADMIN"));
        MockHttpServletRequest request = json("POST", "/api/maintenance/tickets", "read",
                "{\"tenantId\":1,\"machineId\":10}");
        assertEquals(403, execute(request, null).getStatus());
    }

    @Test
    void crossTenantBatchReadIsBlocked() throws Exception {
        stub("read", principal(Set.of("INVENTORY_READ"), Set.of(), Set.of(), "ADMIN"));
        MockHttpServletRequest request = bearer("GET", "/api/inventory/batches", "read");
        request.addParameter("tenantId", "2");
        assertEquals(403, execute(request, null).getStatus());
    }

    @Test
    void crossTenantBottleMutationIsBlocked() throws Exception {
        stub("manage", principal(Set.of("INVENTORY_MANAGE"), Set.of(), Set.of(), "ADMIN"));
        assertEquals(403, execute(json("POST", "/api/inventory/bottles", "manage",
                "{\"tenantId\":2}"), null).getStatus());
    }

    @Test
    void crossTenantMovementHistoryIsBlocked() throws Exception {
        stub("read", principal(Set.of("INVENTORY_READ"), Set.of(), Set.of(), "ADMIN"));
        MockHttpServletRequest request = bearer("GET", "/api/inventory/movements/bottles/9", "read");
        request.addParameter("tenantId", "2");
        assertEquals(403, execute(request, null).getStatus());
    }

    @Test
    void crossTenantMaintenanceTicketIsBlockedFromPersistedOwnership() throws Exception {
        stub("maintenance", principal(Set.of("MAINTENANCE_READ"), Set.of(), Set.of(), "ADMIN"));
        when(tickets.findByIdAndTenantId(8L, 1L)).thenReturn(Optional.empty());
        assertEquals(403, execute(bearer("GET", "/api/maintenance/tickets/8", "maintenance"), null).getStatus());
    }

    @Test
    void technicianWithNoAssignedMachineIsDenied() throws Exception {
        stub("tech-none", principal(Set.of("MAINTENANCE_READ"), Set.of(), Set.of(), "TECHNICIAN"));
        when(tickets.findByIdAndTenantId(8L, 1L)).thenReturn(Optional.of(ticket(8L, 10L, 20L)));
        assertEquals(403, execute(bearer("GET", "/api/maintenance/tickets/8", "tech-none"), null).getStatus());
    }

    @Test
    void technicianAssignedMachineAndLocationIsAllowed() throws Exception {
        stub("tech", principal(Set.of("MAINTENANCE_READ"), Set.of(20L), Set.of(10L), "TECHNICIAN"));
        when(tickets.findByIdAndTenantId(8L, 1L)).thenReturn(Optional.of(ticket(8L, 10L, 20L)));
        AtomicReference<Object> reached = new AtomicReference<>();
        assertEquals(200, execute(bearer("GET", "/api/maintenance/tickets/8", "tech"), reached).getStatus());
        assertTrue(Boolean.TRUE.equals(reached.get()));
    }

    @Test
    void technicianCannotCreateTicketForUnassignedMachine() throws Exception {
        stub("tech-create", principal(Set.of("MAINTENANCE_MANAGE"), Set.of(), Set.of(), "TECHNICIAN"));
        MockHttpServletRequest request = json("POST", "/api/maintenance/tickets", "tech-create",
                "{\"tenantId\":1,\"machineId\":10,\"reportedByActorId\":999}");
        assertEquals(403, execute(request, null).getStatus());
    }

    @Test
    void technicianTicketListIsFilteredToAssignedScope() {
        IdentityPrincipal actor = principal(Set.of("MAINTENANCE_READ"), Set.of(20L), Set.of(10L), "TECHNICIAN");
        List<MaintenanceTicketResponse> filtered = authorization.filterTickets(actor, List.of(
                response(1L, 10L, 20L), response(2L, 11L, 20L), response(3L, 10L, 21L)));
        assertEquals(List.of(1L), filtered.stream().map(MaintenanceTicketResponse::getId).toList());
    }

    @Test
    void authenticatedActorReplacesCallerSuppliedAuditActor() throws Exception {
        stub("adjust", principal(Set.of("INVENTORY_ADJUST"), Set.of(), Set.of(), "ADMIN"));
        AtomicReference<Object> body = new AtomicReference<>();
        MockHttpServletResponse response = execute(json("POST", "/api/inventory/adjustments", "adjust",
                "{\"tenantId\":1,\"actorId\":999,\"reason\":\"count\"}"), body);
        assertEquals(200, response.getStatus());
        assertTrue(body.get().toString().contains("\"actorId\":202"));
    }

    @Test
    void internalMachineCallbackRequiresServerOnlyKey() throws Exception {
        MockHttpServletRequest denied = request("POST", "/api/inventory/consumptions");
        assertEquals(401, execute(denied, null).getStatus());

        MockHttpServletRequest allowed = request("POST", "/api/inventory/consumptions");
        allowed.addHeader("X-Internal-Service-Key", "server-key");
        AtomicReference<Object> reached = new AtomicReference<>();
        assertEquals(200, execute(allowed, reached).getStatus());
        assertTrue(Boolean.TRUE.equals(reached.get()));
        verifyNoInteractions(identity);
    }

    private MockHttpServletResponse execute(MockHttpServletRequest request, AtomicReference<Object> captured)
            throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (secured, ignored) -> {
            if (captured != null) {
                byte[] body = secured.getInputStream().readAllBytes();
                captured.set(body.length == 0 ? Boolean.TRUE : new String(body, StandardCharsets.UTF_8));
            }
        });
        return response;
    }

    private MockHttpServletRequest request(String method, String path) {
        return new MockHttpServletRequest(method, path);
    }

    private MockHttpServletRequest bearer(String method, String path, String token) {
        MockHttpServletRequest request = request(method, path);
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }

    private MockHttpServletRequest json(String method, String path, String token, String body) {
        MockHttpServletRequest request = bearer(method, path, token);
        request.setContentType("application/json");
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        return request;
    }

    private void stub(String token, IdentityPrincipal principal) {
        when(identity.introspect(token)).thenReturn(principal);
    }

    private IdentityPrincipal principal(Set<String> grants, Set<Long> locations, Set<Long> machines, String role) {
        return new IdentityPrincipal(202L, "operation-user", 1L, Set.of(role), grants, locations, machines);
    }

    private MaintenanceTicket ticket(Long id, Long machineId, Long locationId) {
        return MaintenanceTicket.builder().id(id).tenantId(1L).machineId(machineId).locationId(locationId).build();
    }

    private MaintenanceTicketResponse response(Long id, Long machineId, Long locationId) {
        return MaintenanceTicketResponse.builder().id(id).tenantId(1L).machineId(machineId).locationId(locationId).build();
    }

    private ResponseStatusException status(int code) {
        return new ResponseStatusException(HttpStatus.valueOf(code));
    }
}
