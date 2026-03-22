package com.style.seckill.service;

import com.style.seckill.config.RedisFallbackAlertProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RedisFallbackAlertTrackerTest {

    @Test
    void shouldTrackRecentFallbacksAndDegradedState() {
        MutableClock clock = new MutableClock(Instant.parse("2026-03-22T00:00:00Z"), ZoneId.of("UTC"));
        RedisFallbackAlertProperties properties = new RedisFallbackAlertProperties();
        properties.setWindowSeconds(60);
        properties.setThreshold(2);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

        RedisFallbackAlertTracker tracker = new RedisFallbackAlertTracker(properties, meterRegistry, clock);

        tracker.recordFallback();
        tracker.recordFallback();

        assertThat(tracker.recentFallbackCount()).isEqualTo(2);
        assertThat(tracker.isDegraded()).isTrue();
        assertThat(meterRegistry.get("seckill.redis.reserve.fallback.recent").gauge().value()).isEqualTo(2.0);
        assertThat(meterRegistry.get("seckill.redis.reserve.degraded").gauge().value()).isEqualTo(1.0);

        clock.advanceSeconds(61);

        assertThat(tracker.recentFallbackCount()).isZero();
        assertThat(tracker.isDegraded()).isFalse();
    }

    private static final class MutableClock extends Clock {
        private Instant instant;
        private final ZoneId zoneId;

        private MutableClock(Instant instant, ZoneId zoneId) {
            this.instant = instant;
            this.zoneId = zoneId;
        }

        @Override
        public ZoneId getZone() {
            return zoneId;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new MutableClock(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }

        private void advanceSeconds(long seconds) {
            instant = instant.plusSeconds(seconds);
        }
    }
}
