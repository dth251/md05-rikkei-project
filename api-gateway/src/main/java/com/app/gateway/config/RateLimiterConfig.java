package com.app.gateway.config;

import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.support.ConfigurationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

@Configuration
public class RateLimiterConfig {

    @Bean
    RedisRateLimiter redisRateLimiter(
            ReactiveStringRedisTemplate redis,
            @Qualifier(RedisRateLimiter.REDIS_SCRIPT_NAME) RedisScript<List<Long>> script,
            ConfigurationService configurationService
    ) {
        return new FailClosedRedisRateLimiter(redis, script, configurationService);
    }
}
