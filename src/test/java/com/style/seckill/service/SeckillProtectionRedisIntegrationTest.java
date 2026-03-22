package com.style.seckill.service;

import com.style.seckill.config.SeckillRedisProperties;
import com.style.seckill.domain.SeckillEvent;
import com.style.seckill.dto.CaptchaChallengeResponse;
import com.style.seckill.dto.SeckillAccessTokenResponse;
import com.style.seckill.exception.AccessTokenInvalidException;
import com.style.seckill.exception.RateLimitExceededException;
import com.style.seckill.protection.state.ProtectionStateStore;
import com.style.seckill.protection.state.RedisProtectionStateStore;
import com.style.seckill.repository.SeckillEventRepository;
import java.io.IOException;
import java.net.ServerSocket;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import redis.embedded.RedisServer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(properties = {
        "seckill.redis.enabled=true",
        "spring.data.redis.host=localhost"
})
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SeckillProtectionRedisIntegrationTest {

    private static final int REDIS_PORT = findAvailablePort();
    private static final RedisServer REDIS_SERVER = startRedisServer();

    @Autowired
    private SeckillProtectionService seckillProtectionService;

    @Autowired
    private ProtectionStateStore protectionStateStore;

    @Autowired
    private SeckillEventRepository seckillEventRepository;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private SeckillRedisProperties redisProperties;

    private Long eventId;

    @DynamicPropertySource
    static void registerRedisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.port", () -> REDIS_PORT);
        registry.add("spring.datasource.url",
                () -> "jdbc:h2:mem:redis-protection-testdb;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE");
    }

    @AfterAll
    static void stopRedis() {
        try {
            REDIS_SERVER.stop();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to stop embedded Redis server", exception);
        }
    }

    @BeforeEach
    void setUp() {
        flushRedis();
        seckillEventRepository.deleteAll();

        SeckillEvent event = new SeckillEvent();
        event.setName("Redis protection event");
        event.setStartTime(LocalDateTime.now().minusMinutes(5));
        event.setEndTime(LocalDateTime.now().plusMinutes(30));
        event.setTotalStock(5);
        event.setAvailableStock(5);
        eventId = seckillEventRepository.save(event).getId();
    }

    @Test
    void shouldUseRedisBackedProtectionStoreAndConsumeKeys() {
        assertThat(protectionStateStore).isInstanceOf(RedisProtectionStateStore.class);

        CaptchaChallengeResponse captcha = seckillProtectionService.createCaptcha(eventId, "redis-guard-user", "redis-client-1");
        String captchaKey = redisProperties.getCaptchaKeyPrefix() + captcha.challengeId();

        assertThat(stringRedisTemplate.hasKey(captchaKey)).isTrue();
        assertThat(stringRedisTemplate.getExpire(captchaKey)).isPositive();

        SeckillAccessTokenResponse token = seckillProtectionService.issueAccessToken(
                eventId,
                "redis-guard-user",
                captcha.challengeId(),
                solve(captcha.question()),
                "redis-client-1");

        String tokenKey = redisProperties.getAccessTokenKeyPrefix() + token.accessToken();
        assertThat(stringRedisTemplate.hasKey(captchaKey)).isFalse();
        assertThat(stringRedisTemplate.hasKey(tokenKey)).isTrue();
        assertThat(stringRedisTemplate.getExpire(tokenKey)).isPositive();

        seckillProtectionService.assertPurchaseAttemptAllowed(eventId, "redis-guard-user", token.accessToken(), "redis-client-1");
        seckillProtectionService.consumeAccessToken(eventId, "redis-guard-user", token.accessToken(), "redis-client-1");

        assertThat(stringRedisTemplate.hasKey(tokenKey)).isFalse();
        assertThrows(AccessTokenInvalidException.class,
                () -> seckillProtectionService.consumeAccessToken(eventId, "redis-guard-user", token.accessToken(), "redis-client-1"));
    }

    @Test
    void shouldPersistRateLimitStateInRedis() {
        String rateLimitKey = redisProperties.getRateLimitKeyPrefix() + "purchase:" + eventId + ":redis-client-2";

        for (int index = 0; index < 3; index++) {
            assertThrows(AccessTokenInvalidException.class,
                    () -> seckillProtectionService.assertPurchaseAttemptAllowed(eventId, "rate-user", "invalid-token", "redis-client-2"));
        }

        assertThrows(RateLimitExceededException.class,
                () -> seckillProtectionService.assertPurchaseAttemptAllowed(eventId, "rate-user", "invalid-token", "redis-client-2"));

        Long zsetSize = stringRedisTemplate.opsForZSet().zCard(rateLimitKey);
        assertThat(zsetSize).isEqualTo(3L);
        assertThat(stringRedisTemplate.getExpire(rateLimitKey)).isPositive();
    }

    @Test
    void shouldAllowOnlyOneSuccessfulConcurrentTokenConsumption() throws Exception {
        CaptchaChallengeResponse captcha = seckillProtectionService.createCaptcha(eventId, "redis-guard-user-2", "redis-client-3");
        SeckillAccessTokenResponse token = seckillProtectionService.issueAccessToken(
                eventId,
                "redis-guard-user-2",
                captcha.challengeId(),
                solve(captcha.question()),
                "redis-client-3");

        int concurrency = 8;
        ExecutorService executorService = Executors.newFixedThreadPool(concurrency);
        CountDownLatch ready = new CountDownLatch(concurrency);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(concurrency);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger invalidCount = new AtomicInteger();
        List<Throwable> unexpectedErrors = java.util.Collections.synchronizedList(new ArrayList<>());

        for (int index = 0; index < concurrency; index++) {
            executorService.submit(() -> {
                ready.countDown();
                try {
                    start.await(5, TimeUnit.SECONDS);
                    seckillProtectionService.consumeAccessToken(eventId, "redis-guard-user-2", token.accessToken(), "redis-client-3");
                    successCount.incrementAndGet();
                } catch (AccessTokenInvalidException exception) {
                    invalidCount.incrementAndGet();
                } catch (Throwable throwable) {
                    unexpectedErrors.add(throwable);
                } finally {
                    done.countDown();
                }
            });
        }

        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
        executorService.shutdownNow();

        assertThat(unexpectedErrors).isEmpty();
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(invalidCount.get()).isEqualTo(concurrency - 1);
        assertThat(stringRedisTemplate.hasKey(redisProperties.getAccessTokenKeyPrefix() + token.accessToken())).isFalse();
    }

    @Test
    void shouldKeepRedisRateLimitAtomicUnderConcurrency() throws Exception {
        int concurrency = 8;
        ExecutorService executorService = Executors.newFixedThreadPool(concurrency);
        CountDownLatch ready = new CountDownLatch(concurrency);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(concurrency);
        AtomicInteger invalidCount = new AtomicInteger();
        AtomicInteger rateLimitedCount = new AtomicInteger();
        List<Throwable> unexpectedErrors = java.util.Collections.synchronizedList(new ArrayList<>());
        String clientId = "redis-client-4";

        for (int index = 0; index < concurrency; index++) {
            executorService.submit(() -> {
                ready.countDown();
                try {
                    start.await(5, TimeUnit.SECONDS);
                    seckillProtectionService.assertPurchaseAttemptAllowed(eventId, "rate-user", "invalid-token", clientId);
                } catch (AccessTokenInvalidException exception) {
                    invalidCount.incrementAndGet();
                } catch (RateLimitExceededException exception) {
                    rateLimitedCount.incrementAndGet();
                } catch (Throwable throwable) {
                    unexpectedErrors.add(throwable);
                } finally {
                    done.countDown();
                }
            });
        }

        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
        executorService.shutdownNow();

        assertThat(unexpectedErrors).isEmpty();
        assertThat(invalidCount.get()).isEqualTo(3);
        assertThat(rateLimitedCount.get()).isEqualTo(concurrency - 3);
        assertThat(stringRedisTemplate.opsForZSet().zCard(redisProperties.getRateLimitKeyPrefix() + "purchase:" + eventId + ':' + clientId))
                .isEqualTo(3L);
    }

    private void flushRedis() {
        stringRedisTemplate.execute((org.springframework.data.redis.core.RedisCallback<Void>) connection -> {
            connection.serverCommands().flushAll();
            return null;
        });
    }

    private static RedisServer startRedisServer() {
        try {
            RedisServer redisServer = new RedisServer(REDIS_PORT);
            redisServer.start();
            return redisServer;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to start embedded Redis server for integration test", exception);
        }
    }

    private static int findAvailablePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to find available Redis test port", exception);
        }
    }

    private String solve(String question) {
        String expression = question.replace("= ?", "").trim();
        if (expression.contains("+")) {
            String[] parts = expression.split("\\+");
            return Integer.toString(Integer.parseInt(parts[0].trim()) + Integer.parseInt(parts[1].trim()));
        }
        String[] parts = expression.split("-");
        return Integer.toString(Integer.parseInt(parts[0].trim()) - Integer.parseInt(parts[1].trim()));
    }
}
