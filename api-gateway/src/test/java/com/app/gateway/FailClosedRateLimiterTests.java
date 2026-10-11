package com.app.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.app.gateway.config.FailClosedRedisRateLimiter;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.support.ConfigurationService;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;

class FailClosedRateLimiterTests {

    @Test
    @SuppressWarnings("unchecked")
    void redisLuaFailureAfterAvailabilityProbeStillReturns503() {
        var redis = mock(ReactiveStringRedisTemplate.class);
        RedisScript<List<Long>> script = mock(RedisScript.class);
        when(redis.execute(any(RedisScript.class), anyList(), anyList()))
                .thenReturn(Flux.error(new IllegalStateException("Redis interrupted")));
        var limiter = new FailClosedRedisRateLimiter(redis, script, mock(ConfigurationService.class));
        limiter.getConfig().put("login", new RedisRateLimiter.Config().setReplenishRate(2).setBurstCapacity(5));
        var exception = assertThrows(ResponseStatusException.class, () ->
                limiter.isAllowed("login", "client").block(Duration.ofSeconds(2)));
        assertEquals(503, exception.getStatusCode().value());
    }

    @Test
    @SuppressWarnings("unchecked")
    void healthyRedisQuotaDenialRemainsAQuotaResponse() {
        var redis = mock(ReactiveStringRedisTemplate.class);
        RedisScript<List<Long>> script = mock(RedisScript.class);
        when(redis.execute(any(RedisScript.class), anyList(), anyList()))
                .thenReturn(Flux.just(List.of(0L, 0L)));
        var limiter = new FailClosedRedisRateLimiter(redis, script, mock(ConfigurationService.class));
        limiter.getConfig().put("login", new RedisRateLimiter.Config().setReplenishRate(2).setBurstCapacity(5));
        assertFalse(limiter.isAllowed("login", "client").block(Duration.ofSeconds(2)).isAllowed());
    }
}
