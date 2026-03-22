package com.style.seckill.exception;

import com.style.seckill.common.ErrorCode;

public class SoldOutException extends BusinessException {

    public SoldOutException() {
        super(ErrorCode.SOLD_OUT);
    }
}
