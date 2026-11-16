package com.scentstation.reporting.service;

import com.scentstation.reporting.client.MachineSnapshotClient;
import com.scentstation.reporting.client.OperationInventoryClient;
import com.scentstation.reporting.enumtype.ReportPeriod;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.*;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** Real Operation API/database. Records sample events, not physical device execution. */
@EnabledIfEnvironmentVariable(named = "OPERATION_SERVICE_URL", matches = ".+")
class OperationReportingIntegrationTest {
    @Test
    void realInventoryLedgerSeparatesSuccessfulFailedAndDiagnosticCommands() {
        String baseUrl = System.getenv("OPERATION_SERVICE_URL");
        var factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(Duration.ofSeconds(15));
        RestClient api = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
        long tenant = ThreadLocalRandom.current().nextLong(1_000_000_000L, 9_000_000_000L);
        var today = LocalDate.now(ZoneOffset.UTC);
        Map<?, ?> batch = post(api, "/api/inventory/batches", Map.of("tenantId", tenant, "productId", 3,
                "batchCode", "PHASE4-" + UUID.randomUUID(), "receivedQuantity", 1,
                "receivedAt", today.minusDays(1).toString(), "expiryDate", today.plusYears(1).toString()));
        Map<?, ?> bottle = post(api, "/api/inventory/bottles", Map.of("tenantId", tenant, "productId", 3,
                "batchId", batch.get("id"), "bottleCode", "PHASE4-" + UUID.randomUUID(), "initialQuantity", 10));
        Object bottleId = bottle.get("id");
        post(api, "/api/inventory/installations/install", Map.of("tenantId", tenant, "bottleId", bottleId,
                "machineId", tenant, "slotId", 1, "actorId", 1, "measuredQuantityBefore", 10));
        String failedCommand = "PHASE4-" + UUID.randomUUID();
        dispense(api, tenant, bottleId, "PHASE4-" + UUID.randomUUID(), "CUSTOMER", "SUCCESS", "0.50");
        dispense(api, tenant, bottleId, "PHASE4-" + UUID.randomUUID(), "DIAGNOSTIC", "SUCCESS", "0.25");
        var failed = dispense(api, tenant, bottleId, failedCommand, "CUSTOMER", "FAILED", "0.50");
        assertEquals(false, failed.get("inventoryDeducted"));
        var recorded = api.get().uri("/api/inventory/consumptions/command/{command}?tenantId={tenant}", failedCommand, tenant)
                .retrieve().body(Map.class);
        assertNotNull(recorded);
        assertEquals("FAILED", recorded.get("result"));

        var ticket = post(api, "/api/maintenance/tickets", Map.of("tenantId", tenant, "machineId", tenant,
                "title", "Phase 4 test", "description", "Sample maintenance ticket", "category", "TEST",
                "severity", "CRITICAL", "priority", "HIGH", "reportedByActorId", 1, "critical", true,
                "dueAt", LocalDateTime.now(ZoneOffset.UTC).minusDays(1).toString()));
        assertNotNull(ticket.get("id"));
        assertNotNull(api.get().uri("/api/maintenance/machines/{machine}/history?tenantId={tenant}", tenant, tenant)
                .retrieve().body(Map.class));
        var estimation = api.put().uri("/api/inventory/estimations/calibration")
                .body(Map.of("tenantId", tenant, "bottleId", bottleId, "consumptionPerSpray", new BigDecimal("0.5")))
                .retrieve().body(Map.class);
        assertNotNull(estimation);
        assertEquals(2, ((Number) estimation.get("successfulSprayCount")).intValue());

        var client = new OperationInventoryClient(RestClient.builder(), baseUrl);
        var reports = new OperationReportService(client, "UTC", "Asia/Ho_Chi_Minh");
        var dashboard = new DashboardAggregationService(mock(TransactionReportService.class),
                mock(MachineSnapshotClient.class), client, reports).getDashboard(Long.toString(tenant), ReportPeriod.DAY);
        var operations = dashboard.operations();
        assertEquals(1, dashboard.inventory().bottles());
        assertEquals(0, new BigDecimal("9.25").compareTo(client.listBottles(tenant).getFirst().currentQuantity()));
        assertEquals(1, operations.batches().total());
        assertEquals(1, operations.consumption().successfulCustomerCommands());
        assertEquals(1, operations.consumption().successfulDiagnosticCommands());
        assertEquals(0, new BigDecimal("0.50").compareTo(operations.consumption().customerConsumedQuantity()));
        assertEquals(0, new BigDecimal("0.25").compareTo(operations.consumption().diagnosticConsumedQuantity()));
        assertNull(operations.consumption().failedCommands());
        assertEquals(1, operations.maintenance().active());
        assertEquals(1, operations.maintenance().overdue());
        assertEquals(1, operations.maintenance().criticalActive());
        for (String key : new String[]{"operation-service", "operation-batches", "operation-consumption", "operation-maintenance"}) {
            assertEquals("ok", dashboard.upstream().get(key));
        }
        assertEquals(operations, reports.getSummary(tenant, ReportPeriod.DAY));
    }

    private Map<?, ?> dispense(RestClient api, long tenant, Object bottle, String command, String type, String result, String quantity) {
        return post(api, "/api/inventory/consumptions", Map.of("tenantId", tenant, "bottleId", bottle,
                "machineId", tenant, "slotId", 1, "commandId", command, "dispenseType", type,
                "result", result, "consumedQuantity", new BigDecimal(quantity)));
    }

    private Map<?, ?> post(RestClient api, String path, Object body) {
        var result = api.post().uri(path).body(body).retrieve().body(Map.class);
        assertNotNull(result);
        return result;
    }
}
