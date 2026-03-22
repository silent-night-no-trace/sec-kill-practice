package com.style.seckill.exception;

import com.style.seckill.common.ErrorCode;

public class AsyncQueueDisabledException extends BusinessException {

    public AsyncQueueDisabledException() {
        super(ErrorCode.ASYNC_QUEUE_DISABLED);
    }
}
