package com.style.seckill.service;

import com.style.seckill.stock.StockReservationGateway;
import com.style.seckill.stock.StockReservationResult;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class StockReservationCoordinator {

    private static final Logger log = LoggerFactory.getLogger(StockReservationCoordinator.class);

    private final StockReservationGateway stockReservationGateway;
    private final RedisFallbackAlertTracker redisFallbackAlertTracker;
    private final MeterRegistry meterRegistry;

    public StockReservationCoordinator(StockReservationGateway stockReservationGateway,
                                       RedisFallbackAlertTracker redisFallbackAlertTracker,
                                       MeterRegistry meterRegistry) {
        this.stockReservationGateway = stockReservationGateway;
        this.redisFallbackAlertTracker = redisFallbackAlertTracker;
        this.meterRegistry = meterRegistry;
    }

    public ReservationAttempt reserve(Long eventId, String userId) {
        if (!stockReservationGateway.isEnabled()) {
            return new ReservationAttempt(StockReservationResult.RESERVED, false);
        }
        try {
            StockReservationResult result = stockReservationGateway.reserve(eventId, userId);
            return new ReservationAttempt(result, result == StockReservationResult.RESERVED);
        } catch (RuntimeException exception) {
            redisFallbackAlertTracker.recordFallback();
            meterRegistry.counter("seckill.redis.reserve", "outcome", "fallback_db_only").increment();
            log.warn("Redis reservation failed, falling back to DB-only stock deduction for eventId={} userId={}",
                    eventId,
                    userId,
                    exception);
            return new ReservationAttempt(StockReservationResult.RESERVED, false);
        }
    }

    public CompensationOutcome releaseIfNeeded(boolean redisReserved, Long eventId, String userId) {
        if (!redisReserved) {
            meterRegistry.counter("seckill.redis.compensation", "outcome", CompensationOutcome.NOT_REQUIRED.metricTag()).increment();
            return CompensationOutcome.NOT_REQUIRED;
        }
        try {
            boolean released = stockReservationGateway.release(eventId, userId);
            if (!released) {
                meterRegistry.counter("seckill.redis.compensation", "outcome", CompensationOutcome.MARKER_MISSING.metricTag()).increment();
                log.warn("Redis compensation skipped because reservation marker was missing for eventId={} userId={}",
                        eventId,
                        userId);
                return CompensationOutcome.MARKER_MISSING;
            }
            meterRegistry.counter("seckill.redis.compensation", "outcome", CompensationOutcome.RELEASED.metricTag()).increment();
            return CompensationOutcome.RELEASED;
        } catch (RuntimeException exception) {
            meterRegistry.counter("seckill.redis.compensation", "outcome", CompensationOutcome.FAILED.metricTag()).increment();
            log.error("Redis compensation failed for eventId={} userId={}", eventId, userId, exception);
            return CompensationOutcome.FAILED;
        }
    }

    public record ReservationAttempt(StockReservationResult result, boolean redisReserved) {
    }

    public enum CompensationOutcome {
        NOT_REQUIRED,
        RELEASED,
        MARKER_MISSING,
        FAILED;

        public String metricTag() {
            return name().toLowerCase();
        }
    }
}
