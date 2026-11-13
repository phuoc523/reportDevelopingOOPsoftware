package com.scentstation.reporting.client;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.client.RestClient;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class MachineSnapshotClientHttpTest {
    private HttpServer server;
    private ExecutorService executor;
    private MachineSnapshotClient client;
    private volatile String query;

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newCachedThreadPool();
        server.setExecutor(executor);
        server.start();
        client = new MachineSnapshotClient(RestClient.builder(),
                "http://127.0.0.1:" + server.getAddress().getPort());
    }

    @AfterEach
    void stop() {
        server.stop(0);
        executor.shutdownNow();
    }

    private void respond(String path, int status, String body) {
        server.createContext(path, exchange -> {
            query = exchange.getRequestURI().getRawQuery();
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
    }

    @Test
    void machinesUseVerifiedContractAndTenantQuery() {
        respond("/api/machines", 200, """
                [{"id":1,"serialNumber":"SN1","tenantId":"12","locationId":"A",
                  "name":"Kiosk","operationalStatus":"ONLINE","deviceRegistrationStatus":"ACTIVE"}]
                """);
        var machines = client.listMachines("12");
        assertEquals(1, machines.size());
        assertEquals("ONLINE", machines.getFirst().operationalStatus());
        assertEquals("tenantId=12", query);
    }

    @Test
    void locationsUseStringIdsAndInactiveMetadata() {
        respond("/api/locations", 200, """
                [{"locationId":"LOBBY-A","tenantId":"12","name":"Lobby","active":false,
                  "address":"Example","createdAt":"2026-11-13T00:00:00Z"}]
                """);
        var locations = client.listLocations("12");
        assertEquals("LOBBY-A", locations.getFirst().locationId());
        assertFalse(locations.getFirst().active());
        assertEquals("tenantId=12", query);
    }

    @Test
    void alertsDeserializeCurrentContract() {
        respond("/api/alerts", 200, """
                [{"id":2,"tenantId":"12","type":"OFFLINE","severity":"HIGH","status":"OPEN",
                  "machineId":1,"locationId":"A","summary":"Offline machine"}]
                """);
        assertEquals("OPEN", client.listAlerts("12").getFirst().status());
        assertEquals("tenantId=12", query);
    }

    @Test
    void emptyArrayIsConfirmedEmptyData() {
        respond("/api/machines", 200, "[]");
        assertEquals(List.of(), client.listMachines("12"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "null", "{broken", "[null]", "[{}]",
            "[{\"id\":1,\"tenantId\":\"OTHER\",\"operationalStatus\":\"ONLINE\"}]"})
    void incompleteOrCrossTenantMachinesAreUnavailable(String body) {
        respond("/api/machines", 200, body);
        var ex = assertThrows(UpstreamUnavailableException.class, () -> client.listMachines("12"));
        assertEquals("machine-service", ex.getServiceName());
    }

    @ParameterizedTest
    @ValueSource(strings = {"[{}]", "[null]",
            "[{\"locationId\":\"A\",\"tenantId\":\"OTHER\",\"name\":\"Lobby\",\"active\":true}]"})
    void invalidLocationsAreUnavailable(String body) {
        respond("/api/locations", 200, body);
        assertThrows(UpstreamUnavailableException.class, () -> client.listLocations("12"));
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 404, 500, 503})
    void upstreamHttpErrorsAreExplicitlyUnavailable(int status) {
        respond("/api/locations", status, "{}");
        assertThrows(UpstreamUnavailableException.class, () -> client.listLocations("12"));
    }

    @Test
    void realTimeoutIsUnavailable() {
        server.createContext("/api/machines", exchange -> {
            try {
                Thread.sleep(10000);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        assertThrows(UpstreamUnavailableException.class, () -> client.listMachines("12"));
    }

    @Test
    void connectionFailureIsUnavailable() {
        server.stop(0);
        assertThrows(UpstreamUnavailableException.class, () -> client.listMachines("12"));
    }
}
