package com.xuejiai.aaf.module.elder.rehab;

import com.xuejiai.aaf.common.exception.ErrorCode;

/** 康复指导错误码，使用 10_900_000 ~ 10_999_999 段。 */
public interface ErrorCodeConstants {
    ErrorCode REHAB_GUIDE_NOT_FOUND = ErrorCode.of(10_900_000, 404, "康复指导不存在");
    ErrorCode REHAB_GUIDE_INVALID = ErrorCode.of(10_900_001, "康复指导字段不合法");
    ErrorCode REHAB_GUIDE_DOCUMENT_NOT_FOUND = ErrorCode.of(10_900_002, 404, "关联的知识文档不存在");
    ErrorCode REHAB_GUIDE_VERSION_CONFLICT = ErrorCode.of(10_900_003, 409, "康复指导已被其他请求修改，请刷新后重试");
    ErrorCode REHAB_GUIDE_QUERY_INVALID = ErrorCode.of(10_900_004, "康复检索问题不合法");
}
