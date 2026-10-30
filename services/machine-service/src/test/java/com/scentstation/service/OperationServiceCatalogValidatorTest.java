package com.scentstation.service;

import com.sun.net.httpserver.HttpServer;
import com.scentstation.exception.InvalidOperationException;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OperationServiceCatalogValidatorTest {
    @Test
    void validatesCatalogReferencesAndRejectsMissingReferences() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/products/product-1", exchange -> {
            exchange.getResponseHeaders().add("X-Internal-Service-Key", "test-key");
            exchange.sendResponseHeaders("test-key".equals(exchange.getRequestHeaders().getFirst("X-Internal-Service-Key")) ? 200 : 401, -1);
        });
        server.createContext("/api/cartridges/cartridge-1", exchange -> {
            exchange.sendResponseHeaders("test-key".equals(exchange.getRequestHeaders().getFirst("X-Internal-Service-Key")) ? 200 : 401, -1);
        });
        server.createContext("/api/products/missing", exchange -> exchange.sendResponseHeaders(404, -1));
        server.start();

        try {
            OperationServiceCatalogValidator validator = new OperationServiceCatalogValidator(
                "http://127.0.0.1:" + server.getAddress().getPort(), "test-key");
            assertDoesNotThrow(() -> validator.validateReferences("product-1", "cartridge-1", "tenant-1"));
            assertThrows(InvalidOperationException.class,
                () -> validator.validateReferences("missing", "cartridge-1", "tenant-1"));
        } finally {
            server.stop(0);
        }
    }
}
