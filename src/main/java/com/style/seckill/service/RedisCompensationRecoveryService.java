package com.style.seckill.service;

import com.style.seckill.config.RedisCompensationRecoveryProperties;
import com.style.seckill.domain.RedisCompensationSource;
import com.style.seckill.domain.RedisCompensationTask;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class RedisCompensationRecoveryService {

    private final StockReservationCoordinator stockReservationCoordinator;
    private final RedisCompensationTaskService redisCompensationTaskService;
    private final RedisCompensationRecoveryProperties redisCompensationRecoveryProperties;
    private final MeterRegistry meterRegistry;

    public RedisCompensationRecoveryService(StockReservationCoordinator stockReservationCoordinator,
                                            RedisCompensationTaskService redisCompensationTaskService,
                                            RedisCompensationRecoveryProperties redisCompensationRecoveryProperties,
                                            MeterRegistry meterRegistry) {
        this.stockReservationCoordinator = stockReservationCoordinator;
        this.redisCompensationTaskService = redisCompensationTaskService;
        this.redisCompensationRecoveryProperties = redisCompensationRecoveryProperties;
        this.meterRegistry = meterRegistry;
    }

    public StockReservationCoordinator.CompensationOutcome releaseWithRecovery(boolean redisReserved,
                                                                               Long eventId,
                                                                               String userId,
                                                                               RedisCompensationSource source) {
        StockReservationCoordinator.CompensationOutcome outcome = stockReservationCoordinator.releaseIfNeeded(redisReserved, eventId, userId);
        if (outcome == StockReservationCoordinator.CompensationOutcome.FAILED) {
            redisCompensationTaskService.recordFailure(eventId, userId, source, "Redis compensation failed for source " + source);
            return outcome;
        }
        redisCompensationTaskService.markResolved(eventId, userId);
        return outcome;
    }

    public RedisCompensationRetrySummary retryPendingCompensations(int limit) {
        List<RedisCompensationTask> tasks = redisCompensationTaskService.findPendingTasks(limit);
        int resolved = 0;
        int retryFailed = 0;
        int exhausted = 0;
        for (RedisCompensationTask task : tasks) {
            StockReservationCoordinator.CompensationOutcome outcome = stockReservationCoordinator
                    .releaseIfNeeded(true, task.getEventId(), task.getUserId());
            if (outcome == StockReservationCoordinator.CompensationOutcome.FAILED) {
                redisCompensationTaskService.markRetryFailure(task.getId(), "Redis compensation retry failed for source " + task.getSource());
                retryFailed++;
                if (task.getAttemptCount() + 1 >= getMaxAttempts()) {
                    exhausted++;
                }
                continue;
            }
            redisCompensationTaskService.markResolved(task.getEventId(), task.getUserId());
            resolved++;
        }
        meterRegistry.counter("seckill.redis.compensation.retry.processed").increment(tasks.size());
        meterRegistry.counter("seckill.redis.compensation.retry.resolved").increment(resolved);
        meterRegistry.counter("seckill.redis.compensation.retry.failed").increment(retryFailed);
        meterRegistry.counter("seckill.redis.compensation.retry.exhausted").increment(exhausted);
        return new RedisCompensationRetrySummary(tasks.size(), resolved, retryFailed, exhausted);
    }

    private int getMaxAttempts() {
        return redisCompensationRecoveryProperties.getMaxAttempts();
    }
}
