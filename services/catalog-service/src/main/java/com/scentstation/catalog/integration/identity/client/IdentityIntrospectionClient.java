package com.scentstation.catalog.integration.identity.client;

import com.scentstation.catalog.integration.identity.dto.IdentityPrincipal;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

@Component
public class IdentityIntrospectionClient {
    private final RestClient client;
    private final String serviceKey;

    public IdentityIntrospectionClient(
            RestClient.Builder builder,
            @Value("${integration.identity.base-url}") String baseUrl,
            @Value("${security.internal.service-key:}") String serviceKey
    ) {
        var factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()
        );
        factory.setReadTimeout(Duration.ofSeconds(3));
        this.client = builder.clone().baseUrl(baseUrl).requestFactory(factory).build();
        this.serviceKey = serviceKey;
    }

    public IdentityPrincipal introspect(String bearerToken) {
        if (serviceKey == null || serviceKey.isBlank()) {
            throw status(HttpStatus.SERVICE_UNAVAILABLE, "Catalog service authentication is not configured");
        }
        try {
            IdentityPrincipal principal = client.post()
                    .uri("/internal/auth/introspect")
                    .header("Authorization", "Bearer " + bearerToken)
                    .header("X-Internal-Service-Key", serviceKey)
                    .retrieve()
                    .body(IdentityPrincipal.class);
            if (!valid(principal)) {
                throw status(HttpStatus.BAD_GATEWAY, "Invalid Identity response");
            }
            return principal;
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 401 || exception.getStatusCode().value() == 403) {
                throw status(HttpStatus.UNAUTHORIZED, "Invalid or revoked bearer token");
            }
            if (exception.getStatusCode().is5xxServerError()) {
                throw status(HttpStatus.SERVICE_UNAVAILABLE, "Identity verification unavailable");
            }
            throw status(HttpStatus.BAD_GATEWAY, "Invalid Identity response");
        } catch (ResourceAccessException exception) {
            throw status(HttpStatus.SERVICE_UNAVAILABLE, "Identity verification unavailable");
        } catch (RestClientException exception) {
            throw status(HttpStatus.BAD_GATEWAY, "Invalid Identity response");
        }
    }

    private boolean valid(IdentityPrincipal principal) {
        return principal != null
                && principal.id() != null && principal.id() > 0
                && principal.username() != null && !principal.username().isBlank()
                && (principal.tenantId() == null || principal.tenantId() > 0)
                && validStrings(principal.roles())
                && validStrings(principal.grants())
                && validIds(principal.locationIds())
                && validIds(principal.machineIds());
    }

    private boolean validStrings(Set<String> values) {
        return values != null && values.stream().noneMatch(value -> value == null || value.isBlank());
    }

    private boolean validIds(Set<Long> values) {
        return values != null && values.stream().noneMatch(value -> value == null || value <= 0);
    }

    private ResponseStatusException status(HttpStatus status, String reason) {
        return new ResponseStatusException(status, reason);
    }
}
