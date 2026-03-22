package com.style.seckill.exception;

import com.style.seckill.common.ErrorCode;

public class RateLimitExceededException extends BusinessException {

    public RateLimitExceededException() {
        super(ErrorCode.RATE_LIMIT_EXCEEDED);
    }
}
