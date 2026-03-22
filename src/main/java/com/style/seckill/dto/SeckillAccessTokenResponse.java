package com.style.seckill.dto;

import java.time.LocalDateTime;

public record SeckillAccessTokenResponse(Long eventId,
                                         String userId,
                                         String accessToken,
                                         LocalDateTime expiresAt) {
}
