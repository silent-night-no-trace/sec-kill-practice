package com.style.seckill.service;

import com.style.seckill.config.RedisCompensationRecoveryProperties;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "seckill.redis.compensation-recovery", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RedisCompensationRecoveryScheduler {

    private static final Logger log = LoggerFactory.getLogger(RedisCompensationRecoveryScheduler.class);

    private final RedisCompensationRecoveryService redisCompensationRecoveryService;
    private final RedisCompensationRecoveryProperties redisCompensationRecoveryProperties;
    private final MeterRegistry meterRegistry;

    public RedisCompensationRecoveryScheduler(RedisCompensationRecoveryService redisCompensationRecoveryService,
                                              RedisCompensationRecoveryProperties redisCompensationRecoveryProperties,
                                              MeterRegistry meterRegistry) {
        this.redisCompensationRecoveryService = redisCompensationRecoveryService;
        this.redisCompensationRecoveryProperties = redisCompensationRecoveryProperties;
        this.meterRegistry = meterRegistry;
    }

    @Scheduled(fixedDelayString = "${seckill.redis.compensation-recovery.fixed-delay-millis:60000}")
    public void retryCompensations() {
        RedisCompensationRetrySummary summary = redisCompensationRecoveryService.retryPendingCompensations(
                redisCompensationRecoveryProperties.getBatchLimit());
        meterRegistry.counter("seckill.redis.compensation.retry.runs").increment();
        if (summary.processed() > 0) {
            log.warn("Redis compensation retry processed={} resolved={} failed={} exhausted={}",
                    summary.processed(),
                    summary.resolved(),
                    summary.retryFailed(),
                    summary.exhausted());
        }
    }
}
