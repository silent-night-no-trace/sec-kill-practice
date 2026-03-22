package com.style.seckill.exception;

import com.style.seckill.common.ErrorCode;

public class AccessTokenInvalidException extends BusinessException {

    public AccessTokenInvalidException() {
        super(ErrorCode.ACCESS_TOKEN_INVALID);
    }
}
