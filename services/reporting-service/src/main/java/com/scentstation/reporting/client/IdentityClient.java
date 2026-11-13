package com.scentstation.reporting.client;

import com.scentstation.reporting.dto.integration.IdentityActor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;
import org.springframework.web.server.ResponseStatusException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Set;

@Component
public class IdentityClient {
    private final RestClient client;

    public IdentityClient(RestClient.Builder builder, @Value("${integration.identity.base-url}") String baseUrl) {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
        factory.setReadTimeout(Duration.ofSeconds(5));
        client = builder.clone().baseUrl(baseUrl).requestFactory(factory).build();
    }

    public IdentityActor verify(String token) {
        try {
            var actor = client.get().uri("/api/auth/me").headers(headers -> headers.setBearerAuth(token))
                    .retrieve().body(IdentityActor.class);
            if (actor == null || actor.id() == null || actor.id() <= 0
                    || actor.username() == null || actor.username().isBlank()
                    || !valid(actor.roles()) || actor.roles().isEmpty() || !valid(actor.grants())
                    || !valid(actor.locationIds()) || !valid(actor.machineIds())
                    || (actor.tenantId() != null && actor.tenantId() <= 0)
                    || actor.locationIds().stream().anyMatch(id -> id <= 0)
                    || actor.machineIds().stream().anyMatch(id -> id <= 0)) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Invalid Identity response");
            }
            return actor;
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 401 || ex.getStatusCode().value() == 403) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or revoked bearer token");
            }
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Identity verification unavailable");
        } catch (ResourceAccessException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Identity verification unavailable");
        } catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Invalid Identity response");
        }
    }

    private boolean valid(Set<?> values) { return values != null && values.stream().noneMatch(java.util.Objects::isNull); }
}
