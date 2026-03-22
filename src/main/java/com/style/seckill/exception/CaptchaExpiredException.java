package com.style.seckill.exception;

import com.style.seckill.common.ErrorCode;

public class CaptchaExpiredException extends BusinessException {

    public CaptchaExpiredException() {
        super(ErrorCode.CAPTCHA_EXPIRED);
    }
}
