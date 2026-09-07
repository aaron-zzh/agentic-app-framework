package com.xuejiai.aaf.module.chat.message;

import com.xuejiai.aaf.common.exception.ErrorCode;

/** 消息反馈错误码（AAF-114 #11412）。 */
public interface MessageFeedbackErrorCode {

    ErrorCode MESSAGE_FEEDBACK_TYPE_REQUIRED = ErrorCode.of(1_003_100, "反馈类型不能为空");
    ErrorCode MESSAGE_FEEDBACK_TYPE_INVALID =
            ErrorCode.of(1_003_101, "反馈类型仅支持 positive 或 negative");
    ErrorCode MESSAGE_FEEDBACK_MESSAGE_NOT_FOUND = ErrorCode.of(1_003_102, 404, "反馈目标消息不存在");
}
