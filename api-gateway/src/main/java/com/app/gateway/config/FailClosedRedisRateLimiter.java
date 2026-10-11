package com.app.gateway.config;

import java.util.List;
import java.util.Map;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.support.ConfigurationService;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class FailClosedRedisRateLimiter extends RedisRateLimiter {

    public FailClosedRedisRateLimiter(
            ReactiveStringRedisTemplate redis,
            RedisScript<List<Long>> script,
            ConfigurationService configurationService
    ) {
        super(redis, script, configurationService);
    }

    @Override
    public Map<String, String> getHeaders(
            Config config,
            Long tokensLeft
    ) {
        // RedisRateLimiter uses -1 for its allow-traffic fallback on Redis/Lua failure.
        if (tokensLeft == null || tokensLeft < 0) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Rate limiting temporarily unavailable");
        }
        return super.getHeaders(config, tokensLeft);
    }
}
