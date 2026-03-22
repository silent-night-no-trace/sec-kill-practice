package com.style.seckill.exception;

import com.style.seckill.common.ErrorCode;

public class EventNotStartedException extends BusinessException {

    public EventNotStartedException() {
        super(ErrorCode.EVENT_NOT_STARTED);
    }
}
