package com.style.seckill.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "seckill.redis")
public class SeckillRedisProperties {

    private boolean enabled;
    private String stockKeyPrefix = "seckill:stock:";
    private String userSetKeyPrefix = "seckill:users:";
    private String captchaKeyPrefix = "seckill:captcha:";
    private String accessTokenKeyPrefix = "seckill:token:";
    private String rateLimitKeyPrefix = "seckill:ratelimit:";
    private int warmupBatchSize = 100;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getStockKeyPrefix() {
        return stockKeyPrefix;
    }

    public void setStockKeyPrefix(String stockKeyPrefix) {
        this.stockKeyPrefix = stockKeyPrefix;
    }

    public String getUserSetKeyPrefix() {
        return userSetKeyPrefix;
    }

    public void setUserSetKeyPrefix(String userSetKeyPrefix) {
        this.userSetKeyPrefix = userSetKeyPrefix;
    }

    public String getCaptchaKeyPrefix() {
        return captchaKeyPrefix;
    }

    public void setCaptchaKeyPrefix(String captchaKeyPrefix) {
        this.captchaKeyPrefix = captchaKeyPrefix;
    }

    public String getAccessTokenKeyPrefix() {
        return accessTokenKeyPrefix;
    }

    public void setAccessTokenKeyPrefix(String accessTokenKeyPrefix) {
        this.accessTokenKeyPrefix = accessTokenKeyPrefix;
    }

    public String getRateLimitKeyPrefix() {
        return rateLimitKeyPrefix;
    }

    public void setRateLimitKeyPrefix(String rateLimitKeyPrefix) {
        this.rateLimitKeyPrefix = rateLimitKeyPrefix;
    }

    public int getWarmupBatchSize() {
        return warmupBatchSize;
    }

    public void setWarmupBatchSize(int warmupBatchSize) {
        this.warmupBatchSize = warmupBatchSize;
    }
}
