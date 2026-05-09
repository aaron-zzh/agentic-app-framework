package com.xuejiai.aaf.common.exception;

import lombok.Getter;

/**
 * 业务异常。
 *
 * <p>所有业务逻辑错误统一抛此异常，由全局异常处理器捕获并转为 Result 响应。
 */
@Getter
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.message());
        this.code = errorCode.code();
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.code = errorCode.code();
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }
}
