package com.xuejiai.aaf.module.todo;

import com.xuejiai.aaf.common.exception.ErrorCode;

/**
 * TODO 模块错误码，使用 5_000_000 ~ 5_999_999 段。
 *
 * <p>子模块分段：
 *
 * <ul>
 *   <li>TODO：5_000_000 ~ 5_001_999
 * </ul>
 */
public interface ErrorCodeConstants {
    // ========== TODO 模块 5_000_000 ==========
    ErrorCode TODO_NOT_FOUND = ErrorCode.of(5_000_000, 404, "待办不存在");
    ErrorCode TODO_SOURCE_RESOURCE_NOT_FOUND = ErrorCode.of(5_000_001, 404, "待办来源记录不存在");
    ErrorCode TODO_SHARE_COMMAND_INVALID = ErrorCode.of(5_000_002, "待办分享/撤销分享命令不合法");
    ErrorCode TODO_SOURCE_TYPE_REQUIRED = ErrorCode.of(5_000_003, "待办来源类型不能为空");
    ErrorCode TODO_SOURCE_UPDATE_FORBIDDEN = ErrorCode.of(5_000_004, "当前待办来源不可修改");
    ErrorCode TODO_ASSIGNEE_REQUIRED = ErrorCode.of(5_000_005, "待办执行人不能为空");
    ErrorCode TODO_SOURCE_REFERENCE_INVALID = ErrorCode.of(5_000_006, "待办来源引用不合法");
    ErrorCode TODO_VERSION_CONFLICT = ErrorCode.of(5_000_007, 409, "待办已被其他请求修改，请刷新后重试");
    ErrorCode TODO_TITLE_REQUIRED = ErrorCode.of(5_000_008, "待办标题不能为空");
    ErrorCode TODO_CATEGORY_INVALID = ErrorCode.of(5_000_009, "待办分类不合法");
    ErrorCode TODO_STATUS_INVALID = ErrorCode.of(5_000_010, "待办状态不合法");
    ErrorCode TODO_QUEUE_PAYLOAD_INVALID = ErrorCode.of(5_000_011, "待办清理任务载荷不合法");
    ErrorCode TODO_STATUS_TRANSITION_INVALID = ErrorCode.of(5_000_012, "待办状态不允许从 {0} 变更为 {1}");
}
