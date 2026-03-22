package com.style.seckill.protection.state;

public record ProtectionCaptchaState(Long eventId,
                                     String userId,
                                     String clientFingerprint,
                                     String answer,
                                     long expiresAtEpochMillis) {

    public boolean matches(Long expectedEventId, String expectedUserId, String expectedClientFingerprint, String expectedAnswer) {
        return eventId.equals(expectedEventId)
                && userId.equals(expectedUserId)
                && clientFingerprint.equals(expectedClientFingerprint)
                && answer.equals(expectedAnswer);
    }
}
