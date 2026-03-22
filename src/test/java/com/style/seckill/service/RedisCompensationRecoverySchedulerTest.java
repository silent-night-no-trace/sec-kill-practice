package com.style.seckill.service;

import com.style.seckill.config.RedisCompensationRecoveryProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisCompensationRecoverySchedulerTest {

    @Test
    void shouldRetryAndRecordMetrics() {
        RedisCompensationRecoveryService recoveryService = mock(RedisCompensationRecoveryService.class);
        RedisCompensationRecoveryProperties properties = new RedisCompensationRecoveryProperties();
        properties.setBatchLimit(12);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        RedisCompensationRecoveryScheduler scheduler = new RedisCompensationRecoveryScheduler(recoveryService, properties, meterRegistry);

        when(recoveryService.retryPendingCompensations(12)).thenReturn(new RedisCompensationRetrySummary(3, 2, 1, 0));

        scheduler.retryCompensations();

        verify(recoveryService).retryPendingCompensations(12);
        assertThat(meterRegistry.get("seckill.redis.compensation.retry.runs").counter().count()).isEqualTo(1.0);
    }
}
