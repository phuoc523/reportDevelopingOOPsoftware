package com.scentstation.gateway;

import java.time.Duration;
import reactor.core.publisher.Mono;

public interface GatewayRateLimitStore {
    Mono<Long> increment(String key, Duration window);
}
