package com.xuejiai.aaf.module.elder.guard;

import com.xuejiai.aaf.common.exception.ErrorCode;

/** 养老守护（预警）错误码，使用 10_400_000 ~ 10_499_999 段。 */
public interface ErrorCodeConstants {
    ErrorCode ALERT_NOT_FOUND = ErrorCode.of(10_400_002, 404, "预警事件不存在");
    ErrorCode ALERT_HANDLING_NOT_FOUND = ErrorCode.of(10_400_003, 404, "预警响应记录不存在");
    ErrorCode ALERT_STATUS_TRANSITION_INVALID = ErrorCode.of(10_400_008, "预警状态不允许从 {0} 变更为 {1}");
    ErrorCode ALERT_EVENT_DUPLICATED = ErrorCode.of(10_400_009, 409, "设备预警事件已处理");
    ErrorCode ALERT_HANDLING_IMMUTABLE = ErrorCode.of(10_400_010, "预警响应记录只允许新增");
    ErrorCode ALERT_REQUEST_INVALID = ErrorCode.of(10_400_023, 400, "预警请求无效");
    ErrorCode ALERT_VERSION_CONFLICT = ErrorCode.of(10_400_024, 409, "预警数据已被其他请求修改，请刷新后重试");
    ErrorCode ALERT_ALREADY_CLAIMED = ErrorCode.of(10_400_025, 409, "预警已被其他运营人员认领");
    ErrorCode ALERT_CLAIM_REQUIRED = ErrorCode.of(10_400_026, 409, "请先认领预警再代记已知晓");
    ErrorCode ALERT_IMMUTABLE = ErrorCode.of(10_400_027, "预警不允许删除");
    ErrorCode ALERT_CONTACT_INVALID = ErrorCode.of(10_400_028, 400, "代记联系人不属于当前长者");
}
