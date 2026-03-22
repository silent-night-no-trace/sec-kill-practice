package com.style.seckill.service;

import com.style.seckill.config.RedisFallbackAlertProperties;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import org.springframework.stereotype.Component;

@Component
public class RedisFallbackAlertTracker {

    private final RedisFallbackAlertProperties redisFallbackAlertProperties;
    private final Clock clock;
    private final Deque<Long> fallbackTimestamps = new ArrayDeque<>();

    public RedisFallbackAlertTracker(RedisFallbackAlertProperties redisFallbackAlertProperties,
                                     MeterRegistry meterRegistry,
                                     Clock clock) {
        this.redisFallbackAlertProperties = redisFallbackAlertProperties;
        this.clock = clock;

        Gauge.builder("seckill.redis.reserve.fallback.recent", this, RedisFallbackAlertTracker::recentFallbackCount)
                .description("Recent Redis reserve fallback count within the configured alert window")
                .register(meterRegistry);
        Gauge.builder("seckill.redis.reserve.degraded", this, tracker -> tracker.isDegraded() ? 1 : 0)
                .description("Whether Redis reserve fallback frequency crossed the degradation threshold")
                .register(meterRegistry);
    }

    public void recordFallback() {
        if (!redisFallbackAlertProperties.isEnabled()) {
            return;
        }
        synchronized (fallbackTimestamps) {
            cleanupOldEntries(clock.millis());
            fallbackTimestamps.addLast(clock.millis());
        }
    }

    public int recentFallbackCount() {
        synchronized (fallbackTimestamps) {
            cleanupOldEntries(clock.millis());
            return fallbackTimestamps.size();
        }
    }

    public boolean isDegraded() {
        return recentFallbackCount() >= redisFallbackAlertProperties.getThreshold();
    }

    private void cleanupOldEntries(long nowMillis) {
        long windowStart = nowMillis - (redisFallbackAlertProperties.getWindowSeconds() * 1000L);
        while (!fallbackTimestamps.isEmpty() && fallbackTimestamps.peekFirst() < windowStart) {
            fallbackTimestamps.pollFirst();
        }
    }
}
