package com.style.seckill.protection.state;

import com.style.seckill.common.IdGenerator;
import com.style.seckill.config.SeckillRedisProperties;
import com.style.seckill.exception.RateLimitExceededException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "seckill.redis", name = "enabled", havingValue = "true")
public class RedisProtectionStateStore implements ProtectionStateStore {

    private final StringRedisTemplate stringRedisTemplate;
    private final RedisScript<String> popValueRedisScript;
    private final RedisScript<Long> consumeExpectedValueRedisScript;
    private final RedisScript<Long> slidingWindowRateLimitRedisScript;
    private final SeckillRedisProperties redisProperties;
    private final IdGenerator idGenerator;

    public RedisProtectionStateStore(StringRedisTemplate stringRedisTemplate,
                                     @Qualifier("popValueRedisScript") RedisScript<String> popValueRedisScript,
                                     @Qualifier("consumeExpectedValueRedisScript") RedisScript<Long> consumeExpectedValueRedisScript,
                                     @Qualifier("slidingWindowRateLimitRedisScript") RedisScript<Long> slidingWindowRateLimitRedisScript,
                                     SeckillRedisProperties redisProperties,
                                     IdGenerator idGenerator) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.popValueRedisScript = popValueRedisScript;
        this.consumeExpectedValueRedisScript = consumeExpectedValueRedisScript;
        this.slidingWindowRateLimitRedisScript = slidingWindowRateLimitRedisScript;
        this.redisProperties = redisProperties;
        this.idGenerator = idGenerator;
    }

    @Override
    public void enforceRateLimit(String action, Long eventId, String clientFingerprint, int maxRequests, long windowSeconds, long nowMillis) {
        long windowMillis = windowSeconds * 1000L;
        long windowStart = nowMillis - windowMillis;
        String member = nowMillis + ":" + idGenerator.nextCompactId();
        Long allowed = stringRedisTemplate.execute(
                slidingWindowRateLimitRedisScript,
                List.of(rateLimitKey(action, eventId, clientFingerprint)),
                Long.toString(windowStart),
                Long.toString(nowMillis),
                member,
                Integer.toString(maxRequests),
                Long.toString(windowMillis * 2));
        if (allowed == null || allowed != 1L) {
            throw new RateLimitExceededException();
        }
    }

    @Override
    public void saveCaptcha(String challengeId, ProtectionCaptchaState state, long ttlSeconds) {
        stringRedisTemplate.opsForValue().set(captchaKey(challengeId), encodeCaptchaState(state), Duration.ofSeconds(ttlSeconds));
    }

    @Override
    public ProtectionCaptchaState popCaptcha(String challengeId) {
        String encoded = stringRedisTemplate.execute(popValueRedisScript, List.of(captchaKey(challengeId)));
        return encoded == null ? null : decodeCaptchaState(encoded);
    }

    @Override
    public void saveAccessToken(String accessToken, ProtectionAccessTokenState state, long ttlSeconds) {
        stringRedisTemplate.opsForValue().set(accessTokenKey(accessToken), encodeAccessTokenState(state), Duration.ofSeconds(ttlSeconds));
    }

    @Override
    public ProtectionAccessTokenState getAccessToken(String accessToken) {
        String encoded = stringRedisTemplate.opsForValue().get(accessTokenKey(accessToken));
        return encoded == null ? null : decodeAccessTokenState(encoded);
    }

    @Override
    public boolean consumeAccessToken(String accessToken, ProtectionAccessTokenState expectedState) {
        Long removed = stringRedisTemplate.execute(
                consumeExpectedValueRedisScript,
                List.of(accessTokenKey(accessToken)),
                encodeAccessTokenState(expectedState));
        return removed != null && removed == 1L;
    }

    @Override
    public void deleteAccessToken(String accessToken) {
        stringRedisTemplate.delete(accessTokenKey(accessToken));
    }

    private String captchaKey(String challengeId) {
        return redisProperties.getCaptchaKeyPrefix() + challengeId;
    }

    private String accessTokenKey(String accessToken) {
        return redisProperties.getAccessTokenKeyPrefix() + accessToken;
    }

    private String rateLimitKey(String action, Long eventId, String clientFingerprint) {
        return redisProperties.getRateLimitKeyPrefix() + action + ':' + eventId + ':' + clientFingerprint;
    }

    private String encodeCaptchaState(ProtectionCaptchaState state) {
        return encodeValue(state.eventId().toString(), state.userId(), state.clientFingerprint(), state.answer(), Long.toString(state.expiresAtEpochMillis()));
    }

    private ProtectionCaptchaState decodeCaptchaState(String encoded) {
        String[] parts = decodeValue(encoded, 5);
        return new ProtectionCaptchaState(
                Long.parseLong(parts[0]),
                parts[1],
                parts[2],
                parts[3],
                Long.parseLong(parts[4]));
    }

    private String encodeAccessTokenState(ProtectionAccessTokenState state) {
        return encodeValue(state.eventId().toString(), state.userId(), state.clientFingerprint(), Long.toString(state.expiresAtEpochMillis()));
    }

    private ProtectionAccessTokenState decodeAccessTokenState(String encoded) {
        String[] parts = decodeValue(encoded, 4);
        return new ProtectionAccessTokenState(
                Long.parseLong(parts[0]),
                parts[1],
                parts[2],
                Long.parseLong(parts[3]));
    }

    private String encodeValue(String... values) {
        Base64.Encoder encoder = Base64.getUrlEncoder();
        String[] encodedValues = new String[values.length];
        for (int index = 0; index < values.length; index++) {
            encodedValues[index] = encoder.encodeToString(values[index].getBytes(StandardCharsets.UTF_8));
        }
        return String.join(".", encodedValues);
    }

    private String[] decodeValue(String encoded, int expectedParts) {
        String[] encodedParts = encoded.split("\\.", -1);
        if (encodedParts.length != expectedParts) {
            throw new IllegalStateException("Unexpected protection state payload: " + encoded);
        }
        Base64.Decoder decoder = Base64.getUrlDecoder();
        String[] decodedParts = new String[expectedParts];
        for (int index = 0; index < encodedParts.length; index++) {
            decodedParts[index] = new String(decoder.decode(encodedParts[index]), StandardCharsets.UTF_8);
        }
        return decodedParts;
    }
}
