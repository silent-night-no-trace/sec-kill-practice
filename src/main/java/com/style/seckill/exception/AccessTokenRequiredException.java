package com.style.seckill.exception;

import com.style.seckill.common.ErrorCode;

public class AccessTokenRequiredException extends BusinessException {

    public AccessTokenRequiredException() {
        super(ErrorCode.ACCESS_TOKEN_REQUIRED);
    }
}
