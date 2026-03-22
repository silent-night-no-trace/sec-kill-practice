package com.style.seckill.service;

import com.style.seckill.config.AsyncRecoveryProperties;
import com.style.seckill.dto.AsyncRequestReconcileResponse;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AsyncRecoverySchedulerTest {

    @Test
    void shouldReconcileAndRecordMetrics() {
        SeckillAsyncPurchaseService asyncPurchaseService = mock(SeckillAsyncPurchaseService.class);
        AsyncRecoveryProperties properties = new AsyncRecoveryProperties();
        properties.setStaleThresholdSeconds(120);
        properties.setBatchLimit(10);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        AsyncRecoveryScheduler scheduler = new AsyncRecoveryScheduler(asyncPurchaseService, properties, meterRegistry);

        when(asyncPurchaseService.reconcileStaleRequests(120, 10))
                .thenReturn(new AsyncRequestReconcileResponse(120, 10, 2, java.util.List.of("r1", "r2")));

        scheduler.reconcileStaleRequests();

        verify(asyncPurchaseService).reconcileStaleRequests(120, 10);
        assertThat(meterRegistry.get("seckill.async.reconcile.runs").counter().count()).isEqualTo(1.0);
        assertThat(meterRegistry.get("seckill.async.reconcile.marked_failed").counter().count()).isEqualTo(2.0);
    }
}
