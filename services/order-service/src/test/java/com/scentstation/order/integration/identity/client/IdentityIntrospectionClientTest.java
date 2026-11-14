package com.scentstation.order.integration.identity.client;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdentityIntrospectionClientTest {
    private HttpServer server;
    private String baseUrl;

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void sendsBearerAndServiceKeyAndAcceptsValidPrincipal() {
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> serviceKey = new AtomicReference<>();
        respond(200, """
                {"id":7,"username":"customer","tenantId":1,
                 "roles":["CUSTOMER"],"grants":["KIOSK_ORDER"],
                 "locationIds":[],"machineIds":[]}
                """, authorization, serviceKey);

        var principal = client("private-key").introspect("signed-token");

        assertEquals(7L, principal.id());
        assertTrue(principal.grants().contains("KIOSK_ORDER"));
        assertEquals("Bearer signed-token", authorization.get());
        assertEquals("private-key", serviceKey.get());
    }

    @Test
    void mapsRejectedTokenToUnauthorized() {
        respond(401, "{}", null, null);
        assertStatus(401, () -> client("private-key").introspect("revoked"));
    }

    @Test
    void mapsIdentityFailureToServiceUnavailable() {
        respond(500, "{}", null, null);
        assertStatus(503, () -> client("private-key").introspect("token"));
    }

    @Test
    void timeoutFailsClosed() {
        server.createContext("/internal/auth/introspect", exchange -> {
            try {
                Thread.sleep(3500);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            exchange.close();
        });
        server.start();
        assertStatus(503, () -> client("private-key").introspect("token"));
    }

    @Test
    void rejectsMalformedPrincipalAsBadGateway() {
        respond(200, "{}", null, null);
        assertStatus(502, () -> client("private-key").introspect("token"));
    }

    @Test
    void missingServiceKeyFailsClosed() {
        assertStatus(503, () -> client("").introspect("token"));
    }

    private IdentityIntrospectionClient client(String key) {
        return new IdentityIntrospectionClient(RestClient.builder(), baseUrl, key);
    }

    private void respond(int status, String body, AtomicReference<String> authorization,
                         AtomicReference<String> serviceKey) {
        server.createContext("/internal/auth/introspect", exchange -> {
            if (authorization != null) authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            if (serviceKey != null) serviceKey.set(exchange.getRequestHeaders().getFirst("X-Internal-Service-Key"));
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    private void assertStatus(int expected, Runnable request) {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, request::run);
        assertEquals(expected, exception.getStatusCode().value());
    }
}
