package com.xuejiai.aaf.module.ai.aigc;

import com.xuejiai.aaf.common.exception.ErrorCode;

/**
 * AIGC 模块错误码，使用 7_000_000 ~ 7_999_999 段。
 *
 * <p>子模块分段：
 *
 * <ul>
 *   <li>TASK：7_000_000 ~ 7_000_999
 * </ul>
 */
public interface ErrorCodeConstants {

    // ========== TASK 子模块 7_000_000 ==========
    ErrorCode AIGC_TASK_TYPE_INVALID = ErrorCode.of(7_000_000, "不支持的任务类型: {0}");
    ErrorCode AIGC_TASK_NOT_ALLOWED_CREATE = ErrorCode.of(7_000_001, "请使用 /submit 提交任务");
    ErrorCode AIGC_TASK_NOT_ALLOWED_UPDATE = ErrorCode.of(7_000_002, "任务不支持编辑");
    ErrorCode AIGC_TASK_NOT_FOUND = ErrorCode.of(7_000_003, "任务不存在");
    ErrorCode AIGC_TASK_VOICE_TEXT_EMPTY = ErrorCode.of(7_000_004, "配音文本不能为空");
    ErrorCode AIGC_TASK_VOICE_TEXT_TOO_LONG = ErrorCode.of(7_000_005, "配音文本不能超过 {0} 字");
    ErrorCode AIGC_TASK_IMAGE_URL_EMPTY = ErrorCode.of(7_000_006, "图像 URL 不能为空");
    ErrorCode AIGC_TASK_METHOD_EMPTY = ErrorCode.of(7_000_007, "处理方式不能为空");
}
