package com.xuejiai.aaf.module.elder.profile;

import com.xuejiai.aaf.common.exception.ErrorCode;

/** 长者中心错误码，使用 10_000_000 ~ 10_099_999 段。 */
public interface ErrorCodeConstants {
    ErrorCode ELDER_NOT_FOUND = ErrorCode.of(10_000_000, 404, "长者不存在");
    ErrorCode ELDER_CONTACT_NOT_FOUND = ErrorCode.of(10_000_001, 404, "长者联系人不存在");
    ErrorCode ELDER_HEALTH_ITEM_NOT_FOUND = ErrorCode.of(10_000_002, 404, "长者健康条目不存在");
    ErrorCode MEDICATION_PLAN_NOT_FOUND = ErrorCode.of(10_000_003, 404, "用药计划不存在");
    ErrorCode MEDICATION_RECORD_NOT_FOUND = ErrorCode.of(10_000_004, 404, "服药记录不存在");
    ErrorCode ELDER_VERSION_CONFLICT = ErrorCode.of(10_000_005, 409, "长者数据已被其他请求修改，请刷新后重试");
    ErrorCode ELDER_CONTACT_SCOPE_INVALID = ErrorCode.of(10_000_006, "联系人授权范围不合法");
    ErrorCode ELDER_HEALTH_ITEM_TYPE_INVALID = ErrorCode.of(10_000_007, "健康条目类型不合法");
    ErrorCode MEDICATION_PLAN_STATUS_INVALID = ErrorCode.of(10_000_008, "用药计划状态不合法");
    ErrorCode MEDICATION_RECORD_IMMUTABLE = ErrorCode.of(10_000_009, "服药记录只允许新增");
    ErrorCode ELDER_PROFILE_INVALID = ErrorCode.of(10_000_010, "长者档案字段不合法");
    ErrorCode ELDER_CONTACT_INVALID = ErrorCode.of(10_000_011, "长者联系人字段不合法");
    ErrorCode ELDER_CONSENT_INVALID = ErrorCode.of(10_000_012, "启用摄像头与预警需记录同意人和同意时间");
    ErrorCode MEDICATION_PLAN_INVALID = ErrorCode.of(10_000_013, "用药计划字段不合法");
    ErrorCode MEDICATION_RECORD_INVALID = ErrorCode.of(10_000_014, "服药记录字段不合法");
    ErrorCode MEDICATION_RECORD_CONFLICT = ErrorCode.of(10_000_015, 409, "该计划时间已存在不同的服药结果");
}
