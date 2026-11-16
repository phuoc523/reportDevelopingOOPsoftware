package com.scentstation.reporting.client;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.client.RestClient;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class OperationInventoryClientHttpTest {
    private HttpServer server;
    private ExecutorService executor;
    private OperationInventoryClient client;
    private volatile String query;
    private volatile String path;

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newCachedThreadPool();
        server.setExecutor(executor);
        server.start();
        client = new OperationInventoryClient(RestClient.builder(), "http://127.0.0.1:" + server.getAddress().getPort());
    }

    @AfterEach
    void stop() { server.stop(0); executor.shutdownNow(); }

    private void respond(int status, String body) {
        server.createContext("/", exchange -> {
            query = exchange.getRequestURI().getRawQuery();
            path = exchange.getRequestURI().getPath();
            var bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
    }

    private List<?> fetch(String source) {
        return switch (source) {
            case "bottles" -> client.listBottles(12);
            case "batches" -> client.listBatches(12);
            case "movements" -> client.listMovements(12);
            default -> client.listMaintenanceTickets(12);
        };
    }

    @ParameterizedTest
    @ValueSource(strings = {"bottles", "batches", "movements", "tickets"})
    void verifiedDtosDeserializeAndTenantIsSent(String source) {
        String body = switch (source) {
            case "bottles" -> """
                    [{"id":1,"tenantId":12,"productId":3,"currentQuantity":9.5,"status":"INSTALLED","batchId":2}]
                    """;
            case "batches" -> """
                    [{"id":2,"tenantId":12,"productId":3,"receivedQuantity":5,"expired":false,"batchCode":"B"}]
                    """;
            case "movements" -> """
                    [{"id":3,"tenantId":12,"movementType":"CUSTOMER_CONSUMPTION","quantityBefore":10,
                      "quantityAfter":9.5,"difference":-0.5,"referenceId":"CMD1","createdAt":"2026-11-13T01:00:00"}]
                    """;
            default -> """
                    [{"id":4,"tenantId":12,"status":"OPEN","critical":true,"dueAt":"2026-11-13T10:00:00","machineId":1}]
                    """;
        };
        respond(200, body);
        assertEquals(1, fetch(source).size());
        assertEquals("tenantId=12", query);
        assertEquals("tickets".equals(source) ? "/api/maintenance/tickets" : "/api/inventory/" + source, path);
    }

    @ParameterizedTest
    @ValueSource(strings = {"bottles", "batches", "movements", "tickets"})
    void emptyArrayIsConfirmedEmpty(String source) {
        respond(200, "[]");
        assertTrue(fetch(source).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"bottles", "batches", "movements", "tickets"})
    void emptyBodyIsUnavailable(String source) {
        respond(200, "");
        assertThrows(UpstreamUnavailableException.class, () -> fetch(source));
    }

    @ParameterizedTest
    @ValueSource(strings = {"bottles", "batches", "movements", "tickets"})
    void incompleteDataIsUnavailable(String source) {
        respond(200, "[{}]");
        assertThrows(UpstreamUnavailableException.class, () -> fetch(source));
    }

    @ParameterizedTest
    @ValueSource(strings = {"bottles", "batches", "movements", "tickets"})
    void crossTenantDataIsUnavailable(String source) {
        respond(200, """
                [{"id":1,"tenantId":99,"productId":3,"currentQuantity":1,"status":"OPEN",
                  "critical":false,"receivedQuantity":1,"expired":false,"movementType":"REFILL","createdAt":"2026-11-13T00:00:00"}]
                """);
        assertThrows(UpstreamUnavailableException.class, () -> fetch(source));
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 404, 500, 503})
    void httpErrorsAreUnavailable(int status) {
        respond(status, "{}");
        var ex = assertThrows(UpstreamUnavailableException.class, () -> client.listMaintenanceTickets(12));
        assertEquals("operation-service", ex.getServiceName());
    }

    @Test
    void negativeConsumptionCannotBecomeNegativeUsage() {
        respond(200, """
                [{"id":3,"tenantId":12,"movementType":"CUSTOMER_CONSUMPTION","quantityBefore":10,
                  "quantityAfter":11,"referenceId":"CMD1","createdAt":"2026-11-13T01:00:00"}]
                """);
        assertThrows(UpstreamUnavailableException.class, () -> client.listMovements(12));
    }

    @Test
    void malformedJsonIsUnavailable() {
        respond(200, "{invalid");
        assertThrows(UpstreamUnavailableException.class, () -> client.listBatches(12));
    }

    @Test
    void readTimeoutIsUnavailable() {
        server.createContext("/", exchange -> {
            try { Thread.sleep(10000); }
            catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            finally { exchange.close(); }
        });
        assertThrows(UpstreamUnavailableException.class, () -> client.listMovements(12));
    }

    @Test
    void connectionFailureIsUnavailable() {
        server.stop(0);
        assertThrows(UpstreamUnavailableException.class, () -> client.listBatches(12));
    }
}
