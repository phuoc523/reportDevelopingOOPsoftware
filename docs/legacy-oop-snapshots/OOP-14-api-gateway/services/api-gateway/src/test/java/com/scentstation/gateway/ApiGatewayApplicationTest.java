package com.scentstation.gateway;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "management.endpoints.web.exposure.include=health,info"
)
@AutoConfigureWebTestClient
class ApiGatewayApplicationTest {

    private static HttpServer backend;
    private static final AtomicReference<String> lastBackendRequest =
            new AtomicReference<>("");

    static {
        try {
            backend = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            backend.createContext("/", exchange -> {
                String authorization =
                        exchange.getRequestHeaders().getFirst("Authorization");
                String body = "path=" + exchange.getRequestURI().getPath()
                        + ";authorization=" + authorization;

                lastBackendRequest.set(body);
                byte[] response = body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "text/plain");
                exchange.sendResponseHeaders(200, response.length);
                try (var output = exchange.getResponseBody()) {
                    output.write(response);
                }
            });
            backend.start();
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    @Autowired
    private RouteDefinitionLocator routeDefinitionLocator;

    @Autowired
    private WebTestClient webTestClient;

    @LocalServerPort
    private int gatewayPort;

    @org.junit.jupiter.api.BeforeEach
    void configureWebTestClient() {
        webTestClient = WebTestClient.bindToServer()
                .baseUrl("http://127.0.0.1:" + gatewayPort)
                .build();
    }

    @DynamicPropertySource
    static void configureBackend(DynamicPropertyRegistry registry) {
        registry.add(
                "IDENTITY_SERVICE_URL",
                () -> "http://127.0.0.1:" + backend.getAddress().getPort()
        );
    }

    @AfterAll
    static void stopMockIdentityService() {
        if (backend != null) {
            backend.stop(0);
        }
    }

    @Test
    void applicationContextLoads() {
        assertThat(ApiGatewayApplication.class).isNotNull();
    }

    @Test
    void identityRouteUsesConfiguredUriAndApiPaths() {
        List<RouteDefinition> routes =
                routeDefinitionLocator.getRouteDefinitions().collectList().block();

        assertThat(routes).isNotNull();

        RouteDefinition identityRoute = routes.stream()
                .filter(route -> "identity-service".equals(route.getId()))
                .findFirst()
                .orElseThrow();

        assertThat(identityRoute.getUri().toString())
                .startsWith("http://127.0.0.1:");

        String predicates = identityRoute.getPredicates().stream()
                .filter(predicate -> "Path".equals(predicate.getName()))
                .flatMap(predicate -> predicate.getArgs().values().stream())
                .map(Object::toString)
                .reduce("", (left, right) -> left + " " + right);

        assertThat(predicates)
                .contains("/api/auth/**")
                .contains("/api/admin/**")
                .contains("/api/staff/**")
                .contains("/api/customer/**");
    }

    @Test
    void forwardsRequestPathAndAuthorizationHeader() {
        webTestClient.get()
                .uri("/api/auth/health")
                .header("Authorization", "Bearer gateway-test-token")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> {
                    assertThat(body).contains("path=/api/auth/health");
                    assertThat(body)
                            .contains("authorization=Bearer gateway-test-token");
                });

        assertThat(lastBackendRequest.get())
                .contains("authorization=Bearer gateway-test-token");
    }

    @Test
    void allowsCorsPreflightFromConfiguredFrontend() {
        webTestClient.options()
                .uri("/api/auth/health")
                .header("Origin", "http://localhost:3000")
                .header("Access-Control-Request-Method", "GET")
                .exchange()
                .expectStatus().isOk()
                .expectHeader()
                .valueEquals("Access-Control-Allow-Origin", "http://localhost:3000");
    }

    @Test
    void returnsNotFoundForUnroutedPath() {
        webTestClient.get()
                .uri("/not-routed")
                .exchange()
                .expectStatus().isNotFound();
    }
}