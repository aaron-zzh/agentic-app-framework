package com.xuejiai.aaf.common.exception;

import java.text.MessageFormat;

/** 业务异常工具类，支持 {} 占位符格式化消息。 */
public final class ExceptionUtil {

    private ExceptionUtil() {}

    public static BusinessException exception(ErrorCode errorCode) {
        return new BusinessException(errorCode);
    }

    public static BusinessException exception(ErrorCode errorCode, Object... params) {
        String message = MessageFormat.format(errorCode.message(), params);
        return new BusinessException(errorCode.code(), message);
    }
}
