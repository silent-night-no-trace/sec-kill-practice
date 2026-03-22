package com.style.seckill.service;

import com.style.seckill.domain.RedisCompensationSource;
import com.style.seckill.domain.RedisCompensationTaskStatus;
import com.style.seckill.repository.RedisCompensationTaskRepository;
import com.style.seckill.stock.StockReservationGateway;
import com.style.seckill.stock.StockReservationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "seckill.redis.compensation-recovery.retry-delay-seconds=0",
        "seckill.redis.compensation-recovery.max-attempts=2"
})
@ActiveProfiles("test")
class RedisCompensationRecoveryServiceTest {

    @Autowired
    private RedisCompensationRecoveryService redisCompensationRecoveryService;

    @Autowired
    private RedisCompensationTaskRepository redisCompensationTaskRepository;

    @MockBean
    private StockReservationGateway stockReservationGateway;

    @BeforeEach
    void setUp() {
        redisCompensationTaskRepository.deleteAll();
        when(stockReservationGateway.isEnabled()).thenReturn(true);
        when(stockReservationGateway.reserve(1L, "user-1")).thenReturn(StockReservationResult.RESERVED);
    }

    @Test
    void shouldPersistCompensationFailureTaskWhenReleaseFails() {
        doThrow(new IllegalStateException("redis release failed")).when(stockReservationGateway).release(1L, "user-1");

        var outcome = redisCompensationRecoveryService.releaseWithRecovery(true, 1L, "user-1", RedisCompensationSource.SYNC_PURCHASE);

        assertThat(outcome).isEqualTo(StockReservationCoordinator.CompensationOutcome.FAILED);
        var task = redisCompensationTaskRepository.findByEventIdAndUserId(1L, "user-1").orElseThrow();
        assertThat(task.getStatus()).isEqualTo(RedisCompensationTaskStatus.PENDING);
        assertThat(task.getAttemptCount()).isEqualTo(1);
        assertThat(task.getSource()).isEqualTo(RedisCompensationSource.SYNC_PURCHASE);
    }

    @Test
    void shouldResolveTaskWhenRetrySucceeds() {
        when(stockReservationGateway.release(2L, "user-2"))
                .thenThrow(new IllegalStateException("redis release failed"))
                .thenReturn(true);
        redisCompensationRecoveryService.releaseWithRecovery(true, 2L, "user-2", RedisCompensationSource.ASYNC_PROCESS);

        RedisCompensationRetrySummary summary = redisCompensationRecoveryService.retryPendingCompensations(10);

        var task = redisCompensationTaskRepository.findByEventIdAndUserId(2L, "user-2").orElseThrow();
        assertThat(summary.processed()).isEqualTo(1);
        assertThat(summary.resolved()).isEqualTo(1);
        assertThat(task.getStatus()).isEqualTo(RedisCompensationTaskStatus.RESOLVED);
    }

    @Test
    void shouldExhaustTaskAfterMaxAttempts() {
        doThrow(new IllegalStateException("redis release failed")).when(stockReservationGateway).release(3L, "user-3");
        redisCompensationRecoveryService.releaseWithRecovery(true, 3L, "user-3", RedisCompensationSource.ASYNC_ENQUEUE);

        RedisCompensationRetrySummary summary = redisCompensationRecoveryService.retryPendingCompensations(10);

        var task = redisCompensationTaskRepository.findByEventIdAndUserId(3L, "user-3").orElseThrow();
        assertThat(summary.processed()).isEqualTo(1);
        assertThat(summary.retryFailed()).isEqualTo(1);
        assertThat(summary.exhausted()).isEqualTo(1);
        assertThat(task.getStatus()).isEqualTo(RedisCompensationTaskStatus.EXHAUSTED);
        assertThat(task.getAttemptCount()).isEqualTo(2);
    }
}
