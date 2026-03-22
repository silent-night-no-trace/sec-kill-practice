package com.style.seckill.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "seckill.protection")
public class SeckillProtectionProperties {

    private boolean enabled = true;
    private long captchaTtlSeconds = 120;
    private long accessTokenTtlSeconds = 60;
    private int captchaRateLimitRequests = 5;
    private long captchaRateLimitWindowSeconds = 60;
    private int accessTokenRateLimitRequests = 5;
    private long accessTokenRateLimitWindowSeconds = 60;
    private int purchaseRateLimitRequests = 3;
    private long purchaseRateLimitWindowSeconds = 10;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getCaptchaTtlSeconds() {
        return captchaTtlSeconds;
    }

    public void setCaptchaTtlSeconds(long captchaTtlSeconds) {
        this.captchaTtlSeconds = captchaTtlSeconds;
    }

    public long getAccessTokenTtlSeconds() {
        return accessTokenTtlSeconds;
    }

    public void setAccessTokenTtlSeconds(long accessTokenTtlSeconds) {
        this.accessTokenTtlSeconds = accessTokenTtlSeconds;
    }

    public int getCaptchaRateLimitRequests() {
        return captchaRateLimitRequests;
    }

    public void setCaptchaRateLimitRequests(int captchaRateLimitRequests) {
        this.captchaRateLimitRequests = captchaRateLimitRequests;
    }

    public long getCaptchaRateLimitWindowSeconds() {
        return captchaRateLimitWindowSeconds;
    }

    public void setCaptchaRateLimitWindowSeconds(long captchaRateLimitWindowSeconds) {
        this.captchaRateLimitWindowSeconds = captchaRateLimitWindowSeconds;
    }

    public int getAccessTokenRateLimitRequests() {
        return accessTokenRateLimitRequests;
    }

    public void setAccessTokenRateLimitRequests(int accessTokenRateLimitRequests) {
        this.accessTokenRateLimitRequests = accessTokenRateLimitRequests;
    }

    public long getAccessTokenRateLimitWindowSeconds() {
        return accessTokenRateLimitWindowSeconds;
    }

    public void setAccessTokenRateLimitWindowSeconds(long accessTokenRateLimitWindowSeconds) {
        this.accessTokenRateLimitWindowSeconds = accessTokenRateLimitWindowSeconds;
    }

    public int getPurchaseRateLimitRequests() {
        return purchaseRateLimitRequests;
    }

    public void setPurchaseRateLimitRequests(int purchaseRateLimitRequests) {
        this.purchaseRateLimitRequests = purchaseRateLimitRequests;
    }

    public long getPurchaseRateLimitWindowSeconds() {
        return purchaseRateLimitWindowSeconds;
    }

    public void setPurchaseRateLimitWindowSeconds(long purchaseRateLimitWindowSeconds) {
        this.purchaseRateLimitWindowSeconds = purchaseRateLimitWindowSeconds;
    }
}
