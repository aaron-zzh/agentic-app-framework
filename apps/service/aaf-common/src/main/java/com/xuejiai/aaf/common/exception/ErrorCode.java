package com.xuejiai.aaf.common.exception;

/**
 * 错误码接口。
 *
 * <p>各模块实现此接口定义自己的错误码枚举，全局错误码见 {@link GlobalErrorCode}。
 *
 * <p>业务模块使用 {@link ErrorCode#of(int, String)} 快捷创建。
 */
public interface ErrorCode {

    /** 错误码数值 */
    int code();

    /** 错误提示信息 */
    String message();

    /** HTTP 响应状态；未显式指定时沿用既有全局错误码映射。 */
    default int httpStatus() {
        return switch (code()) {
            case 401, 403, 404, 409 -> code();
            default -> 400;
        };
    }

    /** 快捷创建错误码实例。 */
    static ErrorCode of(int code, String message) {
        return new SimpleErrorCode(code, message);
    }

    /** 创建带 HTTP 响应状态的错误码实例。 */
    static ErrorCode of(int code, int httpStatus, String message) {
        return new HttpStatusErrorCode(code, httpStatus, message);
    }

    /** 不可变错误码实现。 */
    record SimpleErrorCode(int code, String message) implements ErrorCode {}

    /** 带 HTTP 响应状态的不可变错误码实现。 */
    record HttpStatusErrorCode(int code, int httpStatus, String message) implements ErrorCode {}
}
