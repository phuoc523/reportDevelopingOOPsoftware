package com.scentstation.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

class TrustedRateLimitIdentityResolverTest {
    @Test
    void derivesUserAndTenantOnlyFromIdentityIntrospection() {
        AtomicReference<ClientRequest> outbound = new AtomicReference<>();
        WebClient.Builder builder = WebClient.builder().exchangeFunction(request -> {
            outbound.set(request);
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header("Content-Type", "application/json")
                    .body("{\"id\":8,\"tenantId\":12}")
                    .build());
        });
        TrustedRateLimitIdentityResolver resolver =
                new TrustedRateLimitIdentityResolver(builder, "http://identity", "internal-key");
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/products")
                .header("Authorization", "Bearer signed-token")
                .header("X-User-Id", "999")
                .header("X-Tenant-Id", "999")
                .build());

        assertEquals("identity:12:8", resolver.resolve(exchange).block());
        assertEquals("Bearer signed-token", outbound.get().headers().getFirst("Authorization"));
        assertEquals("internal-key", outbound.get().headers().getFirst("X-Internal-Service-Key"));
    }

    @Test
    void anonymousKeyUsesSocketAddressAndIgnoresSpoofableIdentityHeaders() {
        TrustedRateLimitIdentityResolver resolver = new TrustedRateLimitIdentityResolver(
                WebClient.builder().exchangeFunction(request -> Mono.error(
                        new AssertionError("anonymous requests must not call Identity"))),
                "http://identity",
                "internal-key"
        );
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/auth/login")
                .remoteAddress(new InetSocketAddress("192.0.2.5", 8080))
                .header("X-User-Id", "999")
                .header("X-Tenant-Id", "999")
                .build());

        assertEquals("anonymous:192.0.2.5", resolver.resolve(exchange).block());
    }
}
