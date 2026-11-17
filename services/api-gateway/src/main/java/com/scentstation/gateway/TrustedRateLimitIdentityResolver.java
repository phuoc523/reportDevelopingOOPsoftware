package com.scentstation.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.InetAddress;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class TrustedRateLimitIdentityResolver implements RateLimitIdentityResolver {
    private static final Duration IDENTITY_TIMEOUT = Duration.ofSeconds(2);

    private final WebClient identityClient;
    private final String internalServiceKey;

    public TrustedRateLimitIdentityResolver(
            WebClient.Builder webClientBuilder,
            @Value("${gateway.rate-limit.identity-service-url:http://localhost:8081}") String identityServiceUrl,
            @Value("${gateway.rate-limit.internal-service-key:}") String internalServiceKey
    ) {
        this.identityClient = webClientBuilder.baseUrl(identityServiceUrl).build();
        this.internalServiceKey = internalServiceKey;
    }

    @Override
    public Mono<String> resolve(ServerWebExchange exchange) {
        String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith("Bearer ")
                || authorization.length() <= 7 || authorization.length() > 8192) {
            return Mono.just(remoteAddressKey(exchange));
        }
        if (internalServiceKey.isBlank()) {
            return Mono.error(new RateLimitIdentityUnavailableException(
                    "Gateway internal service credential is not configured"));
        }
        return identityClient.post()
                .uri("/internal/auth/introspect")
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .header("X-Internal-Service-Key", internalServiceKey)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .timeout(IDENTITY_TIMEOUT)
                .switchIfEmpty(Mono.error(new RateLimitIdentityUnavailableException(
                        "Identity Service returned an empty principal")))
                .map(TrustedRateLimitIdentityResolver::principalKey)
                .onErrorResume(WebClientResponseException.Unauthorized.class,
                        ignored -> Mono.just(remoteAddressKey(exchange)))
                .onErrorMap(exception -> !(exception instanceof RateLimitIdentityUnavailableException),
                        exception -> new RateLimitIdentityUnavailableException(
                                "Identity Service could not verify the rate-limit identity", exception));
    }

    private static String principalKey(JsonNode principal) {
        JsonNode id = principal == null ? null : principal.get("id");
        JsonNode tenantId = principal == null ? null : principal.get("tenantId");
        if (id == null || !id.canConvertToLong() || id.longValue() <= 0) {
            throw new RateLimitIdentityUnavailableException("Identity Service returned an invalid principal");
        }
        String tenant = tenantId == null || tenantId.isNull() ? "platform" : tenantId.asText();
        return "identity:" + tenant + ":" + id.longValue();
    }

    private static String remoteAddressKey(ServerWebExchange exchange) {
        InetAddress address = exchange.getRequest().getRemoteAddress() == null
                ? null
                : exchange.getRequest().getRemoteAddress().getAddress();
        return "anonymous:" + (address == null ? "unknown" : address.getHostAddress());
    }
}
