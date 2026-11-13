package com.scentstation.reporting.security;

import com.scentstation.reporting.client.IdentityClient;
import com.scentstation.reporting.config.ReportingSecurityConfig;
import com.scentstation.reporting.controller.DashboardController;
import com.scentstation.reporting.controller.TransactionSyncController;
import com.scentstation.reporting.dto.integration.IdentityActor;
import com.scentstation.reporting.enumtype.ReportPeriod;
import com.scentstation.reporting.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Isolated security tests mock Identity transport, never bypass the production filter. */
@WebMvcTest(controllers = {ReportingSecurityTest.Probe.class, DashboardController.class, TransactionSyncController.class})
@Import({ReportingSecurityConfig.class, ReportingSecurityTest.Probe.class, InternalApiKeyVerifier.class})
class ReportingSecurityTest {
    @Autowired MockMvc mvc;
    @MockBean IdentityClient identity;
    @MockBean DashboardAggregationService dashboard;
    @MockBean TransactionService transactions;
    @MockBean OrderPaymentSyncService sync;

    @org.junit.jupiter.api.BeforeEach
    void dashboardFixture() {
        when(dashboard.getDashboard(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyBoolean())).thenReturn(new com.scentstation.reporting.dto.DashboardResponse(
                        null, null, null, null, java.util.Map.of(), java.util.List.of(), null));
    }

    @RestController
    static class Probe {
        @GetMapping({"/api/reporting/transactions/summary", "/api/reporting/export/transactions", "/api/reporting/health"})
        String read() { return "ok"; }
    }

    private IdentityActor actor(String role) {
        return new IdentityActor(1L, "unit-user", 12L, Set.of(role), Set.of(), Set.of(), Set.of());
    }

    @Test
    void missingBearerAndForgedHeadersCannotAuthenticate() throws Exception {
        mvc.perform(get("/api/reporting/transactions/summary")
                .header("X-Verified-Roles", "PLATFORM_SUPER_ADMIN").header("X-Verified-Tenant-Id", "12"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(identity, dashboard, transactions);
    }

    @Test
    void realFilterUsesIdentityProfileInsteadOfClientRoleHeaders() throws Exception {
        when(identity.verify("unit-token")).thenReturn(actor("CUSTOMER"));
        mvc.perform(get("/api/reporting/transactions/summary").header("Authorization", "Bearer unit-token")
                        .header("X-Verified-Roles", "PLATFORM_SUPER_ADMIN"))
                .andExpect(status().isForbidden());
    }

    @Test
    void brandDashboardUsesOwnTenantAndExcludesGlobalPayments() throws Exception {
        when(identity.verify("unit-token")).thenReturn(actor("BRAND_ADMIN"));
        mvc.perform(get("/api/reporting/dashboard").param("period", "day").header("Authorization", "Bearer unit-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("access.scope").value("tenant"))
                .andExpect(jsonPath("access.tenantId").value("12"))
                .andExpect(jsonPath("access.canReadFinancialReports").value(false))
                .andExpect(jsonPath("access.canExport").value(false));
        verify(dashboard).getDashboard("12", ReportPeriod.DAY, false);
        mvc.perform(get("/api/reporting/transactions/summary").header("Authorization", "Bearer unit-token"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/reporting/export/transactions").header("Authorization", "Bearer unit-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void crossTenantAndDuplicateTenantRequestsAreRejectedBeforeAggregation() throws Exception {
        when(identity.verify("unit-token")).thenReturn(actor("BRAND_ADMIN"));
        mvc.perform(get("/api/reporting/dashboard").param("tenantId", "99").header("Authorization", "Bearer unit-token"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/reporting/dashboard").param("tenantId", "12", "99").header("Authorization", "Bearer unit-token"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(dashboard);
    }

    @Test
    void assignedScopeCannotReadWholeTenantAggregates() throws Exception {
        when(identity.verify("unit-token")).thenReturn(new IdentityActor(1L, "scoped", 12L,
                Set.of("VIEWER"), Set.of(), Set.of(1L), Set.of()));
        mvc.perform(get("/api/reporting/dashboard").header("Authorization", "Bearer unit-token"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(dashboard);
    }

    @Test
    void encodedDashboardPathCannotBypassAssignedScopeCheck() throws Exception {
        when(identity.verify("unit-token")).thenReturn(new IdentityActor(1L, "scoped", 12L,
                Set.of("VIEWER"), Set.of(), Set.of(1L), Set.of()));
        mvc.perform(get(java.net.URI.create("/api/reporting/%64ashboard"))
                        .header("Authorization", "Bearer unit-token"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(dashboard);
    }

    @Test
    void platformCanReadGlobalReportsAndExport() throws Exception {
        when(identity.verify("unit-token")).thenReturn(actor("PLATFORM_SUPER_ADMIN"));
        mvc.perform(get("/api/reporting/transactions/summary").header("Authorization", "Bearer unit-token"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/reporting/export/transactions").header("Authorization", "Bearer unit-token"))
                .andExpect(status().isOk());
    }

    @Test
    void viewerPrecedenceMatchesIdentityEvenWithMixedPlatformRoleAndExportGrant() throws Exception {
        when(identity.verify("unit-token")).thenReturn(new IdentityActor(1L, "viewer", 12L,
                Set.of("VIEWER", "PLATFORM_SUPER_ADMIN"), Set.of("REPORT_EXPORT"), Set.of(), Set.of()));
        mvc.perform(get("/api/reporting/export/transactions").header("Authorization", "Bearer unit-token"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/reporting/dashboard").header("Authorization", "Bearer unit-token"))
                .andExpect(status().isOk());
        verify(dashboard).getDashboard("12", ReportPeriod.MONTH, false);
    }

    @Test
    void identityOutageFailsClosedWith503() throws Exception {
        when(identity.verify("unit-token")).thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Identity verification unavailable"));
        mvc.perform(get("/api/reporting/dashboard").header("Authorization", "Bearer unit-token"))
                .andExpect(status().isServiceUnavailable());
        verifyNoInteractions(dashboard);
    }

    @Test
    void invalidOrRevokedTokenCannotReachReports() throws Exception {
        when(identity.verify("unit-token")).thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or revoked bearer token"));
        mvc.perform(get("/api/reporting/dashboard").header("Authorization", "Bearer unit-token"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(dashboard);
    }

    @Test
    void healthAndExistingKeyProtectedReplayDoNotDependOnIdentity() throws Exception {
        mvc.perform(get("/api/reporting/health")).andExpect(status().isOk());
        mvc.perform(post("/api/reporting/internal/transactions/sync-from-order/PAY-1")
                        .header("X-Internal-Api-Key", "local-reporting-sync-key"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/reporting/internal/transactions/sync-from-order/PAY-1"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(identity);
    }
}
