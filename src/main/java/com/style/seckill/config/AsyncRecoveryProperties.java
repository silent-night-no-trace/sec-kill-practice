package com.style.seckill.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "seckill.async.recovery")
public class AsyncRecoveryProperties {

    private boolean enabled = true;
    private long staleThresholdSeconds = 300;
    private int batchLimit = 50;
    private long fixedDelayMillis = 60_000;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getStaleThresholdSeconds() {
        return staleThresholdSeconds;
    }

    public void setStaleThresholdSeconds(long staleThresholdSeconds) {
        this.staleThresholdSeconds = staleThresholdSeconds;
    }

    public int getBatchLimit() {
        return batchLimit;
    }

    public void setBatchLimit(int batchLimit) {
        this.batchLimit = batchLimit;
    }

    public long getFixedDelayMillis() {
        return fixedDelayMillis;
    }

    public void setFixedDelayMillis(long fixedDelayMillis) {
        this.fixedDelayMillis = fixedDelayMillis;
    }
}
