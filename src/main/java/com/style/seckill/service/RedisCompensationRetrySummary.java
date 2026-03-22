package com.style.seckill.service;

public record RedisCompensationRetrySummary(int processed,
                                            int resolved,
                                            int retryFailed,
                                            int exhausted) {
}
