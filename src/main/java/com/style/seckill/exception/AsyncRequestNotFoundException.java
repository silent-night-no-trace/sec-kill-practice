package com.style.seckill.exception;

import com.style.seckill.common.ErrorCode;

public class AsyncRequestNotFoundException extends BusinessException {

    public AsyncRequestNotFoundException() {
        super(ErrorCode.ASYNC_REQUEST_NOT_FOUND);
    }
}
