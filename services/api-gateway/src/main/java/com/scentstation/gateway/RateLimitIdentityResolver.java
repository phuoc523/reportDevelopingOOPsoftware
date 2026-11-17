package com.scentstation.gateway;

import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

public interface RateLimitIdentityResolver {
    Mono<String> resolve(ServerWebExchange exchange);
}
