package com.scentstation.gateway;

import java.time.Duration;
import java.util.List;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class RedisGatewayRateLimitStore implements GatewayRateLimitStore {
    private static final DefaultRedisScript<Long> INCREMENT_WINDOW = new DefaultRedisScript<>(
            "local count = redis.call('INCR', KEYS[1]); "
                    + "if count == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]); end; "
                    + "return count;",
            Long.class
    );

    private final ReactiveStringRedisTemplate redis;

    public RedisGatewayRateLimitStore(ReactiveStringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public Mono<Long> increment(String key, Duration window) {
        if (window.isZero() || window.isNegative()) {
            return Mono.error(new IllegalArgumentException("Rate-limit window must be positive"));
        }
        return redis.execute(INCREMENT_WINDOW, List.of(key), List.of(Long.toString(window.toMillis()))).next();
    }
}
