package com.scentstation.reporting.client;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises the real RestClient transport against a controlled HTTP server. */
class OrderPaymentClientHttpTest {
    private HttpServer server;
    private ExecutorService executor;
    private OrderPaymentClient client;

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newCachedThreadPool();
        server.setExecutor(executor);
        server.start();
        client = new OrderPaymentClient(RestClient.builder(),
                "http://127.0.0.1:" + server.getAddress().getPort());
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
        executor.shutdownNow();
    }

    private void respond(int status, String body) {
        server.createContext("/api/v1/payments/PAY-HTTP", exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
    }

    @Test
    void deserializesActualOrderJson() {
        respond(200, """
                {"reference":"PAY-HTTP","orderId":8,"amount":25000,"currency":"VND",
                 "status":"PENDING","createdAt":"2026-11-13T10:00:00",
                 "expiresAt":"2026-11-13T10:15:00","paidAt":null,"qrPayload":"qr"}
                """);
        var payment = client.getPayment("PAY-HTTP");
        assertEquals(8L, payment.orderId());
        assertEquals("PENDING", payment.status());
        assertNull(payment.paidAt());
    }

    @Test
    void invalidJsonReturns502() {
        respond(200, "{invalid");
        assertEquals(HttpStatus.BAD_GATEWAY, assertThrows(ResponseStatusException.class,
                () -> client.getPayment("PAY-HTTP")).getStatusCode());
    }

    @Test
    void realHttp404IsPreserved() {
        respond(404, "{}");
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> client.getPayment("PAY-HTTP")).getStatusCode());
    }

    @Test
    void realHttp503Returns502() {
        respond(503, "{}");
        assertEquals(HttpStatus.BAD_GATEWAY, assertThrows(ResponseStatusException.class,
                () -> client.getPayment("PAY-HTTP")).getStatusCode());
    }

    @Test
    void realReadTimeoutReturns502() {
        server.createContext("/api/v1/payments/PAY-HTTP", exchange -> {
            try {
                Thread.sleep(10000);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        assertEquals(HttpStatus.BAD_GATEWAY, assertThrows(ResponseStatusException.class,
                () -> client.getPayment("PAY-HTTP")).getStatusCode());
    }
}
