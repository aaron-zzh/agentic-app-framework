package com.xuejiai.aaf.common.exception;

/**
 * 错误码接口。
 *
 * <p>各模块实现此接口定义自己的错误码枚举，全局错误码见 {@link GlobalErrorCode}。
 */
public interface ErrorCode {

    /** 错误码数值 */
    int code();

    /** 错误提示信息 */
    String message();
}
