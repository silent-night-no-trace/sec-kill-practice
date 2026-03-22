package com.style.seckill.protection.state;

public interface ProtectionStateStore {

    void enforceRateLimit(String action, Long eventId, String clientFingerprint, int maxRequests, long windowSeconds, long nowMillis);

    void saveCaptcha(String challengeId, ProtectionCaptchaState state, long ttlSeconds);

    ProtectionCaptchaState popCaptcha(String challengeId);

    void saveAccessToken(String accessToken, ProtectionAccessTokenState state, long ttlSeconds);

    ProtectionAccessTokenState getAccessToken(String accessToken);

    boolean consumeAccessToken(String accessToken, ProtectionAccessTokenState expectedState);

    void deleteAccessToken(String accessToken);
}
