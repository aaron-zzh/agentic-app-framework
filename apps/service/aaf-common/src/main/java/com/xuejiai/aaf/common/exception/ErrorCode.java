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

    /** 快捷创建错误码实例 */
    static ErrorCode of(int code, String message) {
        return new SimpleErrorCode(code, message);
    }

    /** 不可变错误码实现 */
    record SimpleErrorCode(int code, String message) implements ErrorCode {}
}
