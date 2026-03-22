package com.style.seckill.exception;

import com.style.seckill.common.ErrorCode;

public class DuplicatePurchaseException extends BusinessException {

    public DuplicatePurchaseException() {
        super(ErrorCode.DUPLICATE_PURCHASE);
    }
}
