package com.scentstation.reporting.client;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class IdentityClientHttpTest {
    private HttpServer server;
    private ExecutorService executor;
    private IdentityClient client;
    private volatile String authorization;

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newCachedThreadPool(); server.setExecutor(executor); server.start();
        client = new IdentityClient(RestClient.builder(), "http://127.0.0.1:" + server.getAddress().getPort());
    }
    @AfterEach
    void stop() { server.stop(0); executor.shutdownNow(); }
    private void respond(int status, String body) {
        server.createContext("/api/auth/me", exchange -> {
            authorization = exchange.getRequestHeaders().getFirst("Authorization");
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes); exchange.close();
        });
    }
    @Test
    void forwardsBearerToIdentityAndReadsServerProfile() {
        respond(200, """
                {"id":1,"username":"user","tenantId":12,"roles":["BRAND_ADMIN"],"grants":[],"locationIds":[],"machineIds":[]}
                """);
        assertEquals(12L, client.verify("unit-token").tenantId());
        assertEquals("Bearer unit-token", authorization);
    }
    @ParameterizedTest
    @ValueSource(ints = {401, 403})
    void invalidOrRevokedTokensReturn401(int status) {
        respond(status, "{}");
        assertEquals(HttpStatus.UNAUTHORIZED, assertThrows(ResponseStatusException.class,
                () -> client.verify("unit-token")).getStatusCode());
    }
    @ParameterizedTest
    @ValueSource(ints = {404, 429, 500, 503})
    void failedVerificationReturns503(int status) {
        respond(status, "{}");
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, assertThrows(ResponseStatusException.class,
                () -> client.verify("unit-token")).getStatusCode());
    }
    @ParameterizedTest
    @ValueSource(strings = {"", "null", "{}", "{invalid",
            "{\"id\":1,\"username\":\"user\",\"roles\":[\"BRAND_ADMIN\"]}"})
    void malformedProfilesCannotAuthorize(String body) {
        respond(200, body);
        assertEquals(HttpStatus.BAD_GATEWAY, assertThrows(ResponseStatusException.class,
                () -> client.verify("unit-token")).getStatusCode());
    }
    @Test
    void realTimeoutFailsClosed() {
        server.createContext("/api/auth/me", exchange -> {
            try { Thread.sleep(10000); }
            catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            finally { exchange.close(); }
        });
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, assertThrows(ResponseStatusException.class,
                () -> client.verify("unit-token")).getStatusCode());
    }
    @Test
    void connectionRefusedFailsClosed() {
        server.stop(0);
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, assertThrows(ResponseStatusException.class,
                () -> client.verify("unit-token")).getStatusCode());
    }
}
