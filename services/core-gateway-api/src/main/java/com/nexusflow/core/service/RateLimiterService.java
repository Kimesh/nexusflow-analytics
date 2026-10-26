package com.nexusflow.core.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@Slf4j
public class RateLimiterService {

    private final StringRedisTemplate redisTemplate;
    private final boolean enabled;
    private final long capacity;

    public RateLimiterService(
            StringRedisTemplate redisTemplate,
            @Value("${nexusflow.rate-limit.enabled:true}") boolean enabled,
            @Value("${nexusflow.rate-limit.capacity:1000}") long capacity) {
        this.redisTemplate = redisTemplate;
        this.enabled = enabled;
        this.capacity = capacity;
    }

    public boolean isAllowed(String clientId) {
        if (!enabled) {
            return true;
        }

        try {
            long currentSecond = System.currentTimeMillis() / 1000;
            String key = "ratelimit:" + clientId + ":" + currentSecond;

            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1) {
                redisTemplate.expire(key, Duration.ofSeconds(2));
            }

            return count != null && count <= capacity;
        } catch (Exception ex) {
            // High-availability fail-open strategy: Do not bring down transaction ingestion if Redis blips
            log.warn("Rate limiter failed to communicate with Redis. Failing open to preserve ingestion SLA: {}", ex.getMessage());
            return true;
        }
    }
}
