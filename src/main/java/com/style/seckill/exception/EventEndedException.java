package com.style.seckill.exception;

import com.style.seckill.common.ErrorCode;

public class EventEndedException extends BusinessException {

    public EventEndedException() {
        super(ErrorCode.EVENT_ENDED);
    }
}
