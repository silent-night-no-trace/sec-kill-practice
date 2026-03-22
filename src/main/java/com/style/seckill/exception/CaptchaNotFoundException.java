package com.style.seckill.exception;

import com.style.seckill.common.ErrorCode;

public class CaptchaNotFoundException extends BusinessException {

    public CaptchaNotFoundException() {
        super(ErrorCode.CAPTCHA_NOT_FOUND);
    }
}
