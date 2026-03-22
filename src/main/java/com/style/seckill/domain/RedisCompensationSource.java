package com.style.seckill.domain;

public enum RedisCompensationSource {
    SYNC_PURCHASE,
    ASYNC_ENQUEUE,
    ASYNC_PROCESS,
    ASYNC_DEAD_LETTER,
    ASYNC_RECONCILE
}
