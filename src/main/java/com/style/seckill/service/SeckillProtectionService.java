package com.style.seckill.service;

import com.style.seckill.common.IdGenerator;
import com.style.seckill.config.SeckillProtectionProperties;
import com.style.seckill.dto.CaptchaChallengeResponse;
import com.style.seckill.dto.SeckillAccessTokenResponse;
import com.style.seckill.exception.AccessTokenExpiredException;
import com.style.seckill.exception.AccessTokenInvalidException;
import com.style.seckill.exception.AccessTokenRequiredException;
import com.style.seckill.exception.CaptchaExpiredException;
import com.style.seckill.exception.CaptchaInvalidException;
import com.style.seckill.exception.CaptchaNotFoundException;
import com.style.seckill.exception.EventNotFoundException;
import com.style.seckill.exception.RateLimitExceededException;
import com.style.seckill.protection.state.ProtectionAccessTokenState;
import com.style.seckill.protection.state.ProtectionCaptchaState;
import com.style.seckill.protection.state.ProtectionStateStore;
import com.style.seckill.repository.SeckillEventRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Service;

@Service
public class SeckillProtectionService {

    private final SeckillEventRepository seckillEventRepository;
    private final SeckillProtectionProperties protectionProperties;
    private final IdGenerator idGenerator;
    private final ProtectionStateStore protectionStateStore;
    private final MeterRegistry meterRegistry;
    private final Clock clock;

    public SeckillProtectionService(SeckillEventRepository seckillEventRepository,
                                    SeckillProtectionProperties protectionProperties,
                                    IdGenerator idGenerator,
                                    ProtectionStateStore protectionStateStore,
                                    MeterRegistry meterRegistry,
                                    Clock clock) {
        this.seckillEventRepository = seckillEventRepository;
        this.protectionProperties = protectionProperties;
        this.idGenerator = idGenerator;
        this.protectionStateStore = protectionStateStore;
        this.meterRegistry = meterRegistry;
        this.clock = clock;
    }

    public CaptchaChallengeResponse createCaptcha(Long eventId, String userId, String clientFingerprint) {
        ensureEventExists(eventId);
        if (!protectionProperties.isEnabled()) {
            return new CaptchaChallengeResponse(eventId, userId, "PROTECTION_DISABLED", "1 + 0 = ?", LocalDateTime.now(clock));
        }

        enforceRateLimit("captcha",
                eventId,
                clientFingerprint,
                protectionProperties.getCaptchaRateLimitRequests(),
                protectionProperties.getCaptchaRateLimitWindowSeconds());

        ArithmeticCaptcha arithmeticCaptcha = ArithmeticCaptcha.random();
        String challengeId = idGenerator.nextCompactId();
        LocalDateTime expiresAt = LocalDateTime.now(clock).plusSeconds(protectionProperties.getCaptchaTtlSeconds());
        protectionStateStore.saveCaptcha(
                challengeId,
                new ProtectionCaptchaState(
                        eventId,
                        userId,
                        clientFingerprint,
                        arithmeticCaptcha.answer(),
                        toEpochMillis(expiresAt)),
                protectionProperties.getCaptchaTtlSeconds());

        return new CaptchaChallengeResponse(eventId, userId, challengeId, arithmeticCaptcha.question(), expiresAt);
    }

    public SeckillAccessTokenResponse issueAccessToken(Long eventId,
                                                       String userId,
                                                       String challengeId,
                                                       String captchaAnswer,
                                                       String clientFingerprint) {
        ensureEventExists(eventId);
        if (!protectionProperties.isEnabled()) {
            return new SeckillAccessTokenResponse(eventId, userId, "PROTECTION_DISABLED", LocalDateTime.now(clock));
        }

        enforceRateLimit("token",
                eventId,
                clientFingerprint,
                protectionProperties.getAccessTokenRateLimitRequests(),
                protectionProperties.getAccessTokenRateLimitWindowSeconds());

        ProtectionCaptchaState challengeState = protectionStateStore.popCaptcha(challengeId);
        if (challengeState == null) {
            recordProtectionReject("token", "captcha_not_found");
            throw new CaptchaNotFoundException();
        }
        if (challengeState.expiresAtEpochMillis() < clock.millis()) {
            recordProtectionReject("token", "captcha_expired");
            throw new CaptchaExpiredException();
        }
        if (!challengeState.matches(eventId, userId, clientFingerprint, normalize(captchaAnswer))) {
            recordProtectionReject("token", "captcha_invalid");
            throw new CaptchaInvalidException();
        }

        String accessToken = idGenerator.nextCompactId();
        LocalDateTime expiresAt = LocalDateTime.now(clock).plusSeconds(protectionProperties.getAccessTokenTtlSeconds());
        protectionStateStore.saveAccessToken(
                accessToken,
                new ProtectionAccessTokenState(eventId, userId, clientFingerprint, toEpochMillis(expiresAt)),
                protectionProperties.getAccessTokenTtlSeconds());
        meterRegistry.counter("seckill.protection.token.issue", "result", "success").increment();
        return new SeckillAccessTokenResponse(eventId, userId, accessToken, expiresAt);
    }

    public void assertPurchaseAttemptAllowed(Long eventId,
                                             String userId,
                                             String accessToken,
                                             String clientFingerprint) {
        if (!protectionProperties.isEnabled()) {
            return;
        }

        ensureEventExists(eventId);
        enforceRateLimit("purchase",
                eventId,
                clientFingerprint,
                protectionProperties.getPurchaseRateLimitRequests(),
                protectionProperties.getPurchaseRateLimitWindowSeconds());

        if (accessToken == null || accessToken.isBlank()) {
            recordProtectionReject("purchase", "access_token_required");
            throw new AccessTokenRequiredException();
        }

        ProtectionAccessTokenState tokenState = protectionStateStore.getAccessToken(accessToken);
        if (tokenState == null) {
            recordProtectionReject("purchase", "access_token_invalid");
            throw new AccessTokenInvalidException();
        }
        if (tokenState.expiresAtEpochMillis() < clock.millis()) {
            protectionStateStore.deleteAccessToken(accessToken);
            recordProtectionReject("purchase", "access_token_expired");
            throw new AccessTokenExpiredException();
        }
        if (!tokenState.matches(eventId, userId, clientFingerprint)) {
            recordProtectionReject("purchase", "access_token_invalid");
            throw new AccessTokenInvalidException();
        }
    }

    public void consumeAccessToken(Long eventId,
                                   String userId,
                                   String accessToken,
                                   String clientFingerprint) {
        if (!protectionProperties.isEnabled()) {
            return;
        }
        if (accessToken == null || clientFingerprint == null) {
            return;
        }

        ProtectionAccessTokenState tokenState = protectionStateStore.getAccessToken(accessToken);
        if (tokenState == null) {
            recordProtectionReject("consume", "access_token_invalid");
            throw new AccessTokenInvalidException();
        }
        if (tokenState.expiresAtEpochMillis() < clock.millis()) {
            protectionStateStore.deleteAccessToken(accessToken);
            recordProtectionReject("consume", "access_token_expired");
            throw new AccessTokenExpiredException();
        }
        if (!tokenState.matches(eventId, userId, clientFingerprint)
                || !protectionStateStore.consumeAccessToken(accessToken, tokenState)) {
            recordProtectionReject("consume", "access_token_invalid");
            throw new AccessTokenInvalidException();
        }
    }

    private void ensureEventExists(Long eventId) {
        seckillEventRepository.findById(eventId).orElseThrow(EventNotFoundException::new);
    }

    private void enforceRateLimit(String action, Long eventId, String clientFingerprint, int maxRequests, long windowSeconds) {
        try {
            protectionStateStore.enforceRateLimit(action, eventId, clientFingerprint, maxRequests, windowSeconds, clock.millis());
        } catch (RateLimitExceededException exception) {
            recordProtectionReject(action, "rate_limit_exceeded");
            throw exception;
        }
    }

    private void recordProtectionReject(String action, String reason) {
        meterRegistry.counter("seckill.protection.reject", "action", action, "reason", reason).increment();
    }

    private String normalize(String answer) {
        return answer == null ? "" : answer.trim();
    }

    private long toEpochMillis(LocalDateTime dateTime) {
        return dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private record ArithmeticCaptcha(String question, String answer) {

        private static ArithmeticCaptcha random() {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            int left = random.nextInt(1, 10);
            int right = random.nextInt(1, 10);
            if (random.nextBoolean()) {
                return new ArithmeticCaptcha(left + " + " + right + " = ?", Integer.toString(left + right));
            }
            if (left < right) {
                int swap = left;
                left = right;
                right = swap;
            }
            return new ArithmeticCaptcha(left + " - " + right + " = ?", Integer.toString(left - right));
        }
    }
}
