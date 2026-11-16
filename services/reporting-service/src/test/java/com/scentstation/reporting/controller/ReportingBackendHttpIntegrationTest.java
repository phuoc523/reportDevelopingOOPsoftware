package com.scentstation.reporting.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Real Reporting HTTP server, MySQL and Identity. Keeps unique sample records. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named = "REPORTING_MYSQL_TEST_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "IDENTITY_SERVICE_URL", matches = ".+")
class ReportingBackendHttpIntegrationTest {
    @LocalServerPort int port;
    @Value("${reporting.sync.api-key}") String syncKey;
    @Autowired JdbcTemplate jdbc;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> System.getenv("REPORTING_MYSQL_TEST_URL"));
        properties.add("spring.datasource.username", () -> System.getenv("REPORTING_MYSQL_TEST_USERNAME"));
        properties.add("spring.datasource.password", () -> System.getenv("REPORTING_MYSQL_TEST_PASSWORD"));
    }

    @Test
    void httpSyncPreservesOnePaidRowAndEnforcesBothSecurityBoundaries() {
        var factory = new JdkClientHttpRequestFactory(); factory.setReadTimeout(Duration.ofSeconds(15));
        var api = RestClient.builder().baseUrl("http://127.0.0.1:" + port).requestFactory(factory).build();
        assertEquals(200, getStatus(api, "/api/reporting/health", null));
        String reference = "HTTP-" + UUID.randomUUID();
        var created = LocalDateTime.now().withNano(0).minusMinutes(1);
        Map<String, Object> pending = new LinkedHashMap<>(Map.of("reference", reference, "orderId", "HTTP-TEST",
                "amount", 25000, "currency", "VND", "status", "PENDING", "createdAt", created.toString()));
        int unauthorized = api.post().uri("/api/reporting/internal/transactions/sync").body(pending)
                .exchange((request, response) -> response.getStatusCode().value());
        assertEquals(401, unauthorized);
        var first = sync(api, pending);
        assertEquals("PENDING", first.get("status"));
        assertEquals(first.get("id"), sync(api, pending).get("id"));
        Map<String, Object> paid = new LinkedHashMap<>(pending);
        paid.put("status", "PAID"); paid.put("paidAt", created.plusSeconds(30).toString());
        var updated = sync(api, paid);
        assertEquals(first.get("id"), updated.get("id"));
        assertEquals("PAID", updated.get("status"));
        int staleStatus = api.post().uri("/api/reporting/internal/transactions/sync")
                .header("X-Internal-Api-Key", syncKey).body(pending)
                .exchange((request, response) -> response.getStatusCode().value());
        assertEquals(409, staleStatus);
        assertEquals(1, jdbc.queryForObject("select count(*) from transactions where reference = ?", Integer.class, reference));
        assertEquals("PAID", jdbc.queryForObject("select status from transactions where reference = ?", String.class, reference));
        assertEquals(401, getStatus(api, "/api/reporting/transactions/reference/" + reference, null));

        var identity = RestClient.builder().baseUrl(System.getenv("IDENTITY_SERVICE_URL")).requestFactory(factory).build();
        String suffix = UUID.randomUUID().toString();
        var registered = identity.post().uri("/api/auth/register").body(Map.of("username", "backend-" + suffix,
                "email", "backend-" + suffix + "@example.test", "password", UUID.randomUUID().toString(),
                "fullName", "Reporting backend integration test")).retrieve().body(Map.class);
        assertNotNull(registered);
        String token = (String) registered.get("token");
        assertEquals(403, getStatus(api, "/api/reporting/transactions/reference/" + reference, token));
        identity.post().uri("/api/auth/logout").headers(headers -> headers.setBearerAuth(token)).retrieve().toBodilessEntity();
        assertEquals(401, getStatus(api, "/api/reporting/transactions/reference/" + reference, token));
    }

    private Map<?, ?> sync(RestClient api, Map<String, Object> body) {
        var response = api.post().uri("/api/reporting/internal/transactions/sync")
                .header("X-Internal-Api-Key", syncKey).body(body).retrieve().body(Map.class);
        assertNotNull(response); return response;
    }

    private int getStatus(RestClient api, String path, String token) {
        return api.get().uri(path).headers(headers -> { if (token != null) { headers.setBearerAuth(token); } })
                .exchange((request, response) -> response.getStatusCode().value());
    }
}
