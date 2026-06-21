package com.xuejiai.aaf.framework.protection;

/** 限流触发时抛出，上层 GlobalExceptionHandler 捕获并返回 429。 */
public class RateLimitExceededException extends RuntimeException {

    public RateLimitExceededException(String message) {
        super(message);
    }
}
