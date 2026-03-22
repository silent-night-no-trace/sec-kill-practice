package com.style.seckill.exception;

import com.style.seckill.common.ErrorCode;

public class CaptchaInvalidException extends BusinessException {

    public CaptchaInvalidException() {
        super(ErrorCode.CAPTCHA_INVALID);
    }
}
