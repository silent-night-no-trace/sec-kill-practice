package com.style.seckill.dto;

import java.time.LocalDateTime;

public record CaptchaChallengeResponse(Long eventId,
                                       String userId,
                                       String challengeId,
                                       String question,
                                       LocalDateTime expiresAt) {
}
