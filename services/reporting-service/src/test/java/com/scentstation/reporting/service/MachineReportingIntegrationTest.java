package com.scentstation.reporting.service;

import com.scentstation.reporting.client.MachineSnapshotClient;
import com.scentstation.reporting.client.OperationInventoryClient;
import com.scentstation.reporting.enumtype.ReportPeriod;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** Real Machine API, with unrelated Reporting database/Operation dependencies mocked. */
@EnabledIfEnvironmentVariable(named = "MACHINE_SERVICE_URL", matches = ".+")
class MachineReportingIntegrationTest {
    @Test
    void realMachinesAreGroupedByLocationAndOperationalStatus() {
        String baseUrl = System.getenv("MACHINE_SERVICE_URL");
        var factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(Duration.ofSeconds(15));
        RestClient api = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
        String tenant = "PHASE3-" + UUID.randomUUID();
        String lobby = tenant + "-A";
        String store = tenant + "-B";
        String empty = tenant + "-C";
        for (String location : new String[]{lobby, store, empty}) {
            api.post().uri("/api/locations").body(Map.of("locationId", location, "tenantId", tenant,
                    "name", location)).retrieve().toBodilessEntity();
        }
        register(api, tenant, lobby, "ONLINE");
        register(api, tenant, lobby, "OFFLINE");
        register(api, tenant, store, "MAINTENANCE");

        var client = new MachineSnapshotClient(RestClient.builder(), baseUrl);
        var aggregation = new DashboardAggregationService(mock(TransactionReportService.class),
                client, mock(OperationInventoryClient.class), mock(OperationReportService.class));
        var dashboard = aggregation.getDashboard(tenant, ReportPeriod.DAY);
        assertEquals("ok", dashboard.upstream().get("machine-service"));
        assertEquals("ok", dashboard.upstream().get("machine-locations"));
        assertEquals("ok", dashboard.upstream().get("machine-alerts"));
        assertEquals(3, dashboard.machines().total());
        assertEquals(1, dashboard.machines().online());
        assertEquals(1, dashboard.machines().offline());
        assertEquals(1, dashboard.machines().maintenance());
        assertEquals(3, dashboard.machineLocations().size());
        assertEquals(2, dashboard.machineLocations().get(0).machines().total());
        assertEquals(1, dashboard.machineLocations().get(1).machines().maintenance());
        assertEquals(0, dashboard.machineLocations().get(2).machines().total());
        assertTrue(dashboard.machineLocations().stream().allMatch(row -> row.metadataAvailable()));
    }

    private void register(RestClient api, String tenant, String location, String status) {
        String identity = "PHASE3-" + UUID.randomUUID();
        Map<?, ?> machine = api.post().uri("/api/machines").body(Map.of("serialNumber", identity,
                "deviceIdentity", identity, "tenantId", tenant, "locationId", location, "name", "Phase 3 test"))
                .retrieve().body(Map.class);
        assertNotNull(machine);
        api.patch().uri("/api/machines/{id}/operational-status", machine.get("id"))
                .body(Map.of("status", status)).retrieve().toBodilessEntity();
        assertNotNull(api.get().uri("/api/machines/{id}/availability", machine.get("id"))
                .retrieve().body(Map.class));
        assertNotNull(api.get().uri("/api/machines/{id}/history", machine.get("id"))
                .retrieve().body(java.util.List.class));
    }
}
