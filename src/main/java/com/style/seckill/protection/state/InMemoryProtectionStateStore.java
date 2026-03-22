package com.style.seckill.protection.state;

import com.style.seckill.config.SeckillProtectionProperties;
import com.style.seckill.exception.RateLimitExceededException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "seckill.redis", name = "enabled", havingValue = "false", matchIfMissing = true)
public class InMemoryProtectionStateStore implements ProtectionStateStore {

    private static final long CLEANUP_INTERVAL_MILLIS = 30_000L;

    private final Map<String, ProtectionCaptchaState> captchaStore = new ConcurrentHashMap<>();
    private final Map<String, ProtectionAccessTokenState> accessTokenStore = new ConcurrentHashMap<>();
    private final Map<String, Deque<Long>> rateLimitStore = new ConcurrentHashMap<>();
    private final AtomicLong nextCleanupTimeMillis = new AtomicLong();
    private final SeckillProtectionProperties protectionProperties;

    public InMemoryProtectionStateStore(SeckillProtectionProperties protectionProperties) {
        this.protectionProperties = protectionProperties;
    }

    @Override
    public void enforceRateLimit(String action, Long eventId, String clientFingerprint, int maxRequests, long windowSeconds, long nowMillis) {
        clearExpiredEntriesIfDue(nowMillis);
        String rateKey = rateKey(action, eventId, clientFingerprint);
        long windowStart = nowMillis - (windowSeconds * 1000L);
        Deque<Long> bucket = rateLimitStore.computeIfAbsent(rateKey, ignored -> new ArrayDeque<>());
        synchronized (bucket) {
            while (!bucket.isEmpty() && bucket.peekFirst() < windowStart) {
                bucket.pollFirst();
            }
            if (bucket.size() >= maxRequests) {
                throw new RateLimitExceededException();
            }
            bucket.addLast(nowMillis);
        }
    }

    @Override
    public void saveCaptcha(String challengeId, ProtectionCaptchaState state, long ttlSeconds) {
        captchaStore.put(challengeId, state);
    }

    @Override
    public ProtectionCaptchaState popCaptcha(String challengeId) {
        return captchaStore.remove(challengeId);
    }

    @Override
    public void saveAccessToken(String accessToken, ProtectionAccessTokenState state, long ttlSeconds) {
        accessTokenStore.put(accessToken, state);
    }

    @Override
    public ProtectionAccessTokenState getAccessToken(String accessToken) {
        return accessTokenStore.get(accessToken);
    }

    @Override
    public boolean consumeAccessToken(String accessToken, ProtectionAccessTokenState expectedState) {
        return accessTokenStore.remove(accessToken, expectedState);
    }

    @Override
    public void deleteAccessToken(String accessToken) {
        accessTokenStore.remove(accessToken);
    }

    private void clearExpiredEntriesIfDue(long nowMillis) {
        long scheduledCleanup = nextCleanupTimeMillis.get();
        if (nowMillis < scheduledCleanup) {
            return;
        }
        if (!nextCleanupTimeMillis.compareAndSet(scheduledCleanup, nowMillis + CLEANUP_INTERVAL_MILLIS)) {
            return;
        }

        captchaStore.entrySet().removeIf(entry -> entry.getValue().expiresAtEpochMillis() < nowMillis);
        accessTokenStore.entrySet().removeIf(entry -> entry.getValue().expiresAtEpochMillis() < nowMillis);
        cleanupRateLimitBuckets(nowMillis);
    }

    private void cleanupRateLimitBuckets(long nowMillis) {
        rateLimitStore.entrySet().removeIf(entry -> isBucketExpired(entry.getKey(), entry.getValue(), nowMillis));
    }

    private boolean isBucketExpired(String rateKey, Deque<Long> bucket, long nowMillis) {
        long windowStart = nowMillis - (windowSecondsByAction(rateKey) * 1000L);
        synchronized (bucket) {
            while (!bucket.isEmpty() && bucket.peekFirst() < windowStart) {
                bucket.pollFirst();
            }
            return bucket.isEmpty();
        }
    }

    private long windowSecondsByAction(String rateKey) {
        if (rateKey.startsWith("captcha:")) {
            return protectionProperties.getCaptchaRateLimitWindowSeconds();
        }
        if (rateKey.startsWith("token:")) {
            return protectionProperties.getAccessTokenRateLimitWindowSeconds();
        }
        return protectionProperties.getPurchaseRateLimitWindowSeconds();
    }

    private String rateKey(String action, Long eventId, String clientFingerprint) {
        return action + ':' + eventId + ':' + clientFingerprint;
    }
}
