package com.style.seckill.protection.state;

public record ProtectionAccessTokenState(Long eventId,
                                         String userId,
                                         String clientFingerprint,
                                         long expiresAtEpochMillis) {

    public boolean matches(Long expectedEventId, String expectedUserId, String expectedClientFingerprint) {
        return eventId.equals(expectedEventId)
                && userId.equals(expectedUserId)
                && clientFingerprint.equals(expectedClientFingerprint);
    }
}
