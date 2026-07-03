package com.xuejiai.aaf.common.exception;

import lombok.RequiredArgsConstructor;

/**
 * 全局错误码，占用 [0, 999]。
 *
 * <p>业务模块错误码分段约定：
 *
 * <ul>
 *   <li>system 模块：[1_000_000, 1_999_999]
 *   <li>document 模块：[2_000_000, 2_999_999]
 *   <li>chat 模块：[3_000_000, 3_999_999]
 *   <li>auto-dev 模块：[4_000_000, 4_999_999]
 *   <li>license 模块：[5_000_000, 5_999_999]
 *   <li>pay 模块：[6_000_000, 6_999_999]
 *   <li>ai.aigc 模块：[7_000_000, 7_999_999]
 * </ul>
 *
 * 各模块在自己的包内定义 {@code enum XxxErrorCode implements ErrorCode}。
 */
@RequiredArgsConstructor
public enum GlobalErrorCode implements ErrorCode {
    SUCCESS(0, "成功"),

    // ==================== 客户端错误 ====================
    BAD_REQUEST(400, "请求参数不正确"),
    UNAUTHORIZED(401, "账号未登录"),
    FORBIDDEN(403, "没有该操作权限"),
    NOT_FOUND(404, "请求未找到"),
    METHOD_NOT_ALLOWED(405, "请求方法不正确"),
    TOO_MANY_REQUESTS(429, "请求过于频繁"),

    // ==================== 服务端错误 ====================
    INTERNAL_SERVER_ERROR(500, "系统异常"),
    SERVICE_UNAVAILABLE(503, "服务不可用"),

    // ==================== 自定义通用错误 ====================
    REPEATED_REQUESTS(900, "重复请求"),
    DEMO_DENY(901, "演示模式，禁止操作");

    private final int code;
    private final String message;

    @Override
    public int code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }
}
