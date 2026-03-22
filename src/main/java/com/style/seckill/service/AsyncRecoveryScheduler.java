package com.style.seckill.service;

import com.style.seckill.config.AsyncRecoveryProperties;
import com.style.seckill.dto.AsyncRequestReconcileResponse;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "seckill.async.recovery", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AsyncRecoveryScheduler {

    private static final Logger log = LoggerFactory.getLogger(AsyncRecoveryScheduler.class);

    private final SeckillAsyncPurchaseService seckillAsyncPurchaseService;
    private final AsyncRecoveryProperties asyncRecoveryProperties;
    private final MeterRegistry meterRegistry;

    public AsyncRecoveryScheduler(SeckillAsyncPurchaseService seckillAsyncPurchaseService,
                                  AsyncRecoveryProperties asyncRecoveryProperties,
                                  MeterRegistry meterRegistry) {
        this.seckillAsyncPurchaseService = seckillAsyncPurchaseService;
        this.asyncRecoveryProperties = asyncRecoveryProperties;
        this.meterRegistry = meterRegistry;
    }

    @Scheduled(fixedDelayString = "${seckill.async.recovery.fixed-delay-millis:60000}")
    public void reconcileStaleRequests() {
        AsyncRequestReconcileResponse response = seckillAsyncPurchaseService.reconcileStaleRequests(
                asyncRecoveryProperties.getStaleThresholdSeconds(),
                asyncRecoveryProperties.getBatchLimit());

        meterRegistry.counter("seckill.async.reconcile.runs").increment();
        meterRegistry.counter("seckill.async.reconcile.marked_failed").increment(response.markedFailed());

        if (response.markedFailed() > 0) {
            log.warn("Async reconcile marked {} stale requests as failed: {}",
                    response.markedFailed(),
                    response.requestIds());
        }
    }
}
