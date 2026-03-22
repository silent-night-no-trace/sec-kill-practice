package com.style.seckill.exception;

import com.style.seckill.common.ErrorCode;

public class AsyncQueueUnavailableException extends BusinessException {

    public AsyncQueueUnavailableException() {
        super(ErrorCode.ASYNC_QUEUE_UNAVAILABLE);
    }
}
