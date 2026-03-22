package com.style.seckill.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RedisHealthConfigurationTest {

    private final RedisHealthConfiguration redisHealthConfiguration = new RedisHealthConfiguration();

    @Test
    void shouldReportUpWhenRedisPingSucceeds() {
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        RedisConnection connection = mock(RedisConnection.class);
        when(template.execute(org.mockito.ArgumentMatchers.<RedisCallback<String>>any())).thenAnswer(invocation -> {
            RedisCallback<String> callback = invocation.getArgument(0, RedisCallback.class);
            return callback.doInRedis(connection);
        });
        when(connection.ping()).thenReturn("PONG");

        Health health = redisHealthConfiguration.redisHealthIndicator(template).health();

        assertThat(health.getStatus().getCode()).isEqualTo("UP");
        assertThat(health.getDetails()).containsEntry("ping", "PONG");
    }

    @Test
    void shouldReportDownWhenRedisPingThrows() {
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        doThrow(new IllegalStateException("redis unavailable")).when(template)
                .execute(org.mockito.ArgumentMatchers.<RedisCallback<String>>any());

        HealthIndicator healthIndicator = redisHealthConfiguration.redisHealthIndicator(template);
        Health health = healthIndicator.health();

        assertThat(health.getStatus().getCode()).isEqualTo("DOWN");
        assertThat(health.getDetails()).containsKey("error");
    }
}
