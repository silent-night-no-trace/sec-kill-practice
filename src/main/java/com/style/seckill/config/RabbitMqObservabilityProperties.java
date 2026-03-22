package com.style.seckill.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "seckill.rabbitmq.observability")
public class RabbitMqObservabilityProperties {

    private boolean enabled = true;
    private long queueDepthPollIntervalMillis = 5000;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getQueueDepthPollIntervalMillis() {
        return queueDepthPollIntervalMillis;
    }

    public void setQueueDepthPollIntervalMillis(long queueDepthPollIntervalMillis) {
        this.queueDepthPollIntervalMillis = queueDepthPollIntervalMillis;
    }
}
