package com.style.seckill.config;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
@ConditionalOnProperty(prefix = "seckill.redis", name = "enabled", havingValue = "true")
public class RedisHealthConfiguration {

    @Bean(name = "redisHealthIndicator")
    public HealthIndicator redisHealthIndicator(StringRedisTemplate stringRedisTemplate) {
        return () -> {
            try {
                String ping = stringRedisTemplate.execute((RedisCallback<String>) connection -> connection.ping());
                if (!"PONG".equalsIgnoreCase(ping)) {
                    return Health.down().withDetail("ping", ping == null ? "null" : ping).build();
                }
                return Health.up().withDetail("ping", ping).build();
            } catch (RuntimeException exception) {
                return Health.down(exception).build();
            }
        };
    }
}
