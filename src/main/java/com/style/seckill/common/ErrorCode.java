package com.style.seckill.common;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    EVENT_NOT_FOUND("EVENT_NOT_FOUND", "Seckill event not found", HttpStatus.NOT_FOUND),
    EVENT_NOT_STARTED("EVENT_NOT_STARTED", "Seckill event has not started", HttpStatus.CONFLICT),
    EVENT_ENDED("EVENT_ENDED", "Seckill event has ended", HttpStatus.CONFLICT),
    SOLD_OUT("SOLD_OUT", "Seckill event is sold out", HttpStatus.CONFLICT),
    DUPLICATE_PURCHASE("DUPLICATE_PURCHASE", "User has already purchased this event", HttpStatus.CONFLICT),
    RATE_LIMIT_EXCEEDED("RATE_LIMIT_EXCEEDED", "Request rate limit exceeded", HttpStatus.TOO_MANY_REQUESTS),
    CAPTCHA_NOT_FOUND("CAPTCHA_NOT_FOUND", "Captcha challenge not found", HttpStatus.BAD_REQUEST),
    CAPTCHA_EXPIRED("CAPTCHA_EXPIRED", "Captcha challenge has expired", HttpStatus.BAD_REQUEST),
    CAPTCHA_INVALID("CAPTCHA_INVALID", "Captcha answer is invalid", HttpStatus.BAD_REQUEST),
    ACCESS_TOKEN_REQUIRED("ACCESS_TOKEN_REQUIRED", "Seckill access token is required", HttpStatus.BAD_REQUEST),
    ACCESS_TOKEN_INVALID("ACCESS_TOKEN_INVALID", "Seckill access token is invalid", HttpStatus.BAD_REQUEST),
    ACCESS_TOKEN_EXPIRED("ACCESS_TOKEN_EXPIRED", "Seckill access token has expired", HttpStatus.BAD_REQUEST),
    ASYNC_QUEUE_DISABLED("ASYNC_QUEUE_DISABLED", "Async purchase queue is disabled", HttpStatus.SERVICE_UNAVAILABLE),
    ASYNC_QUEUE_UNAVAILABLE("ASYNC_QUEUE_UNAVAILABLE", "Async purchase queue is unavailable", HttpStatus.SERVICE_UNAVAILABLE),
    ASYNC_PROCESSING_EXHAUSTED("ASYNC_PROCESSING_EXHAUSTED", "Async purchase processing exhausted all retries", HttpStatus.CONFLICT),
    ASYNC_REQUEST_STALE("ASYNC_REQUEST_STALE", "Async purchase request remained pending beyond reconciliation threshold", HttpStatus.CONFLICT),
    ASYNC_REQUEST_NOT_FOUND("ASYNC_REQUEST_NOT_FOUND", "Async purchase request not found", HttpStatus.NOT_FOUND),
    VALIDATION_ERROR("VALIDATION_ERROR", "Request validation failed", HttpStatus.BAD_REQUEST),
    INTERNAL_ERROR("INTERNAL_ERROR", "Internal server error", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;

    ErrorCode(String code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
