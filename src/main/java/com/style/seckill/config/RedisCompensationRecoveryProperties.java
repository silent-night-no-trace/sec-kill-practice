package com.style.seckill.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "seckill.redis.compensation-recovery")
public class RedisCompensationRecoveryProperties {

    private boolean enabled = true;
    private long fixedDelayMillis = 60_000;
    private int batchLimit = 50;
    private long retryDelaySeconds = 60;
    private int maxAttempts = 10;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getFixedDelayMillis() {
        return fixedDelayMillis;
    }

    public void setFixedDelayMillis(long fixedDelayMillis) {
        this.fixedDelayMillis = fixedDelayMillis;
    }

    public int getBatchLimit() {
        return batchLimit;
    }

    public void setBatchLimit(int batchLimit) {
        this.batchLimit = batchLimit;
    }

    public long getRetryDelaySeconds() {
        return retryDelaySeconds;
    }

    public void setRetryDelaySeconds(long retryDelaySeconds) {
        this.retryDelaySeconds = retryDelaySeconds;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }
}
