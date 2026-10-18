package com.scentstation.gateway;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "server.port=0",
                "management.endpoints.web.exposure.include=health,info"
        }
)
class ApiGatewayApplicationTest {

    @Autowired
    private RouteDefinitionLocator routeDefinitionLocator;

    @Test
    void applicationContextLoads() {
        assertThat(ApiGatewayApplication.class).isNotNull();
    }

    @Test
    void identityRouteUsesConfiguredDefaultUriAndApiPaths() {
        List<RouteDefinition> routes =
                routeDefinitionLocator.getRouteDefinitions().collectList().block();

        assertThat(routes).isNotNull();

        RouteDefinition identityRoute = routes.stream()
                .filter(route -> "identity-service".equals(route.getId()))
                .findFirst()
                .orElseThrow();

        assertThat(identityRoute.getUri().toString())
                .isEqualTo("http://localhost:8081");

        String predicateArguments = identityRoute.getPredicates().stream()
                .filter(predicate -> "Path".equals(predicate.getName()))
                .flatMap(predicate -> predicate.getArgs().values().stream())
                .map(Object::toString)
                .reduce("", (left, right) -> left + " " + right);

        assertThat(predicateArguments)
                .contains("/api/auth/**")
                .contains("/api/admin/**")
                .contains("/api/staff/**")
                .contains("/api/customer/**");
    }

    @Test
    void corsConfigurationContainsExpectedOriginsAndMethods() throws Exception {
        String configuration = Files.readString(
                Path.of("src/main/resources/application.yml"));

        assertThat(configuration)
                .contains("globalcors:")
                .contains("allowedOrigins:")
                .contains("http://localhost:3000")
                .contains("http://localhost:5173")
                .contains("allowCredentials: true");

        for (String method : List.of(
                "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")) {
            assertThat(configuration).contains("- " + method);
        }
    }
}