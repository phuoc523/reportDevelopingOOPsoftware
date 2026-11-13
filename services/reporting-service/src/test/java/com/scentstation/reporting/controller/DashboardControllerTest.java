package com.scentstation.reporting.controller;

import com.scentstation.reporting.dto.DashboardResponse;
import com.scentstation.reporting.enumtype.ReportPeriod;
import com.scentstation.reporting.service.DashboardAggregationService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DashboardControllerTest {
    private final DashboardAggregationService service = mock(DashboardAggregationService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new DashboardController(service)).build();

    @org.junit.jupiter.api.BeforeEach
    void setVerifiedUnitPrincipal() {
        var actor = new com.scentstation.reporting.dto.integration.IdentityActor(1L, "unit-platform", null,
                java.util.Set.of("PLATFORM_SUPER_ADMIN"), java.util.Set.of(), java.util.Set.of(), java.util.Set.of());
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(actor, null, List.of()));
    }

    @org.junit.jupiter.api.AfterEach
    void clearPrincipal() { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }

    @Test
    void existingDashboardExposesLocationDistributionAdditively() throws Exception {
        var counts = new DashboardResponse.MachineStatusSummary(1, 1, 0, 0, 0, 0, 0, 0);
        when(service.getDashboard("12", ReportPeriod.MONTH, true)).thenReturn(new DashboardResponse(
                null, counts, null, null, Map.of("machine-service", "ok", "machine-locations", "ok"),
                List.of(new DashboardResponse.MachineLocationSummary("A", "Lobby", true, true, counts)), null));
        mvc.perform(get("/api/reporting/dashboard").param("tenantId", "12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("machines.total").value(1))
                .andExpect(jsonPath("machineLocations[0].name").value("Lobby"))
                .andExpect(jsonPath("machineLocations[0].machines.online").value(1))
                .andExpect(jsonPath("access.scope").value("platform"))
                .andExpect(jsonPath("access.canReadFinancialReports").value(true))
                .andExpect(jsonPath("access.canExport").value(true))
                .andExpect(jsonPath("upstream.machine-locations").value("ok"));
    }

    @Test
    void invalidPeriodDoesNotCallUpstreamServices() throws Exception {
        mvc.perform(get("/api/reporting/dashboard").param("period", "invalid"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void operationSummaryPreservesUnknownFailureCountAsNull() throws Exception {
        var consumption = new com.scentstation.reporting.dto.OperationReportResponse.ConsumptionSummary(
                java.time.LocalDate.of(2026, 11, 13), java.time.LocalDate.of(2026, 11, 13),
                1, 1, new java.math.BigDecimal("0.5"), new java.math.BigDecimal("0.25"), null);
        var operations = new com.scentstation.reporting.dto.OperationReportResponse(null, consumption, null,
                Map.of("operation-failures", "unsupported-missing-list-api"));
        when(service.getDashboard("12", ReportPeriod.DAY, true)).thenReturn(new DashboardResponse(
                null, null, null, null, operations.upstream(), List.of(), operations));
        mvc.perform(get("/api/reporting/dashboard").param("tenantId", "12").param("period", "day"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("operations.consumption.successfulCustomerCommands").value(1))
                .andExpect(jsonPath("operations.consumption.successfulDiagnosticCommands").value(1))
                .andExpect(jsonPath("operations.consumption.failedCommands").value(org.hamcrest.Matchers.nullValue()));
    }
}
