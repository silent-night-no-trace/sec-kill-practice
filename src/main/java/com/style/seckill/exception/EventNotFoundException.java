package com.style.seckill.exception;

import com.style.seckill.common.ErrorCode;

public class EventNotFoundException extends BusinessException {

    public EventNotFoundException() {
        super(ErrorCode.EVENT_NOT_FOUND);
    }
}
