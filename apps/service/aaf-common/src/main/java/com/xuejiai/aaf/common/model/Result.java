package com.xuejiai.aaf.common.model;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonIgnore;

import com.xuejiai.aaf.common.exception.ErrorCode;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;

/**
 * 统一响应结果。
 *
 * @param code 错误码，0 表示成功
 * @param message 提示信息
 * @param data 响应数据
 * @param <T> 数据泛型
 */
public record Result<T>(int code, String message, T data) implements Serializable {

    public static <T> Result<T> success(T data) {
        return new Result<>(
                GlobalErrorCode.SUCCESS.code(), GlobalErrorCode.SUCCESS.message(), data);
    }

    public static <T> Result<T> success() {
        return success(null);
    }

    public static <T> Result<T> error(ErrorCode errorCode) {
        return new Result<>(errorCode.code(), errorCode.message(), null);
    }

    public static <T> Result<T> error(int code, String message) {
        return new Result<>(code, message, null);
    }

    public static <T> Result<T> error(ErrorCode errorCode, String message) {
        return new Result<>(errorCode.code(), message, null);
    }

    @JsonIgnore
    public boolean isSuccess() {
        return code == GlobalErrorCode.SUCCESS.code();
    }
}
