package com.scentstation.order.client;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MachineServiceClientTest {
    private HttpServer server;
    private String baseUrl;

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void submitUsesInternalServiceKey() {
        AtomicReference<String> key = response("/api/machines/dispense-commands");
        var request = new MachineCommandRequest("1", 7L, "PAY-7", "11", "12", "CMD-7");

        MachineCommandResponse result = client().submit(request);

        assertEquals("internal-key", key.get());
        assertEquals("CMD-7", result.commandId());
    }

    @Test
    void resultPollingUsesInternalServiceKey() {
        AtomicReference<String> key = response("/api/machines/dispense-commands/CMD-7");

        MachineCommandResponse result = client().getResult("CMD-7");

        assertEquals("internal-key", key.get());
        assertEquals("DISPENSED", result.status());
    }

    private MachineServiceClient client() {
        return new MachineServiceClient(RestClient.builder(), baseUrl, "internal-key");
    }

    private AtomicReference<String> response(String path) {
        AtomicReference<String> key = new AtomicReference<>();
        server.createContext(path, exchange -> {
            key.set(exchange.getRequestHeaders().getFirst("X-Internal-Service-Key"));
            byte[] bytes = "{\"orderId\":7,\"commandId\":\"CMD-7\",\"status\":\"DISPENSED\",\"message\":\"ok\"}"
                .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        return key;
    }
}
