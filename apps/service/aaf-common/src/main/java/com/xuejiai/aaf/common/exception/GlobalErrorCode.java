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
    SORT_FORMAT_INVALID(400, "排序格式非法，应为 field:asc|desc（多字段用逗号分隔）"),
    SORT_FIELD_NOT_SUPPORTED(400, "不支持排序字段: {0}"),
    CRUD_RESOURCE_NOT_FOUND(404, "{0}不存在"),
    CRUD_RESOURCE_NOT_IN_QUERY_WINDOW(404, "{0}不在当前查询窗口中"),
    CRUD_FILTER_UNSUPPORTED(400, "当前资源不支持筛选条件"),
    CRUD_BATCH_READ_LIMIT_EXCEEDED(400, "批量读取数量不得超过 {0} 条"),
    CRUD_RESTORE_UNSUPPORTED(400, "{0}恢复能力未启用"),
    CRUD_QUERY_WINDOW_EXPIRED(400, "查询窗口已失效"),
    CRUD_FIELD_SET_UNSUPPORTED(400, "不支持的字段集: {0}"),
    CRUD_OPERATION_UNSUPPORTED(400, "{0}{1}能力未启用"),
    CRUD_FILTER_FORMAT_INVALID(400, "筛选条件格式非法: {0}"),
    CRUD_FILTER_OPERATOR_UNSUPPORTED(400, "不支持的筛选操作符: {0}"),
    CRUD_READONLY_CREATE_UNSUPPORTED(400, "只读资源不支持创建"),
    CRUD_READONLY_UPDATE_UNSUPPORTED(400, "只读资源不支持更新"),
    CRUD_READONLY_DELETE_UNSUPPORTED(400, "只读资源不支持删除"),
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
