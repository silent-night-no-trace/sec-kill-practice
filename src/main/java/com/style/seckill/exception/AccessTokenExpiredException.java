package com.style.seckill.exception;

import com.style.seckill.common.ErrorCode;

public class AccessTokenExpiredException extends BusinessException {

    public AccessTokenExpiredException() {
        super(ErrorCode.ACCESS_TOKEN_EXPIRED);
    }
}
