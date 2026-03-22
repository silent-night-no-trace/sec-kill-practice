package com.style.seckill.service;

import com.style.seckill.domain.SeckillEvent;
import com.style.seckill.dto.CaptchaChallengeResponse;
import com.style.seckill.dto.SeckillAccessTokenResponse;
import com.style.seckill.exception.AccessTokenInvalidException;
import com.style.seckill.exception.CaptchaInvalidException;
import com.style.seckill.exception.RateLimitExceededException;
import com.style.seckill.repository.SeckillEventRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
class SeckillProtectionServiceTest {

    @Autowired
    private SeckillProtectionService seckillProtectionService;

    @Autowired
    private SeckillEventRepository seckillEventRepository;

    private Long eventId;

    @BeforeEach
    void setUp() {
        seckillEventRepository.deleteAll();

        SeckillEvent event = new SeckillEvent();
        event.setName("Protection event");
        event.setStartTime(LocalDateTime.now().minusMinutes(5));
        event.setEndTime(LocalDateTime.now().plusMinutes(30));
        event.setTotalStock(5);
        event.setAvailableStock(5);
        eventId = seckillEventRepository.save(event).getId();
    }

    @Test
    void shouldIssueCaptchaAndAccessTokenSuccessfully() {
        CaptchaChallengeResponse captcha = seckillProtectionService.createCaptcha(eventId, "guard-user-1", "client-1");

        SeckillAccessTokenResponse token = seckillProtectionService.issueAccessToken(
                eventId,
                "guard-user-1",
                captcha.challengeId(),
                solve(captcha.question()),
                "client-1");

        assertThat(token.eventId()).isEqualTo(eventId);
        assertThat(token.userId()).isEqualTo("guard-user-1");
        assertThat(token.accessToken()).isNotBlank();
    }

    @Test
    void shouldRejectInvalidCaptchaAnswer() {
        CaptchaChallengeResponse captcha = seckillProtectionService.createCaptcha(eventId, "guard-user-2", "client-2");

        assertThrows(CaptchaInvalidException.class,
                () -> seckillProtectionService.issueAccessToken(eventId, "guard-user-2", captcha.challengeId(), "999", "client-2"));
    }

    @Test
    void shouldRejectReusedAccessToken() {
        CaptchaChallengeResponse captcha = seckillProtectionService.createCaptcha(eventId, "guard-user-3", "client-3");
        SeckillAccessTokenResponse token = seckillProtectionService.issueAccessToken(
                eventId,
                "guard-user-3",
                captcha.challengeId(),
                solve(captcha.question()),
                "client-3");

        seckillProtectionService.assertPurchaseAttemptAllowed(eventId, "guard-user-3", token.accessToken(), "client-3");
        seckillProtectionService.consumeAccessToken(eventId, "guard-user-3", token.accessToken(), "client-3");

        assertThrows(AccessTokenInvalidException.class,
                () -> seckillProtectionService.consumeAccessToken(eventId, "guard-user-3", token.accessToken(), "client-3"));
    }

    @Test
    void shouldRateLimitRepeatedPurchaseAttempts() {
        for (int index = 0; index < 3; index++) {
            assertThrows(AccessTokenInvalidException.class,
                    () -> seckillProtectionService.assertPurchaseAttemptAllowed(eventId, "guard-user-4", "invalid-token", "client-4"));
        }

        assertThrows(RateLimitExceededException.class,
                () -> seckillProtectionService.assertPurchaseAttemptAllowed(eventId, "guard-user-4", "invalid-token", "client-4"));
    }

    @Test
    void shouldRejectAccessTokenFromDifferentClientFingerprint() {
        CaptchaChallengeResponse captcha = seckillProtectionService.createCaptcha(eventId, "guard-user-5", "client-5");
        SeckillAccessTokenResponse token = seckillProtectionService.issueAccessToken(
                eventId,
                "guard-user-5",
                captcha.challengeId(),
                solve(captcha.question()),
                "client-5");

        assertThrows(AccessTokenInvalidException.class,
                () -> seckillProtectionService.assertPurchaseAttemptAllowed(eventId, "guard-user-5", token.accessToken(), "different-client"));
    }

    @Test
    void shouldAllowTokenPreviewRetryBeforeConsumption() {
        CaptchaChallengeResponse captcha = seckillProtectionService.createCaptcha(eventId, "guard-user-6", "client-6");
        SeckillAccessTokenResponse token = seckillProtectionService.issueAccessToken(
                eventId,
                "guard-user-6",
                captcha.challengeId(),
                solve(captcha.question()),
                "client-6");

        seckillProtectionService.assertPurchaseAttemptAllowed(eventId, "guard-user-6", token.accessToken(), "client-6");
        seckillProtectionService.assertPurchaseAttemptAllowed(eventId, "guard-user-6", token.accessToken(), "client-6");
        seckillProtectionService.consumeAccessToken(eventId, "guard-user-6", token.accessToken(), "client-6");

        assertThrows(AccessTokenInvalidException.class,
                () -> seckillProtectionService.consumeAccessToken(eventId, "guard-user-6", token.accessToken(), "client-6"));
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
