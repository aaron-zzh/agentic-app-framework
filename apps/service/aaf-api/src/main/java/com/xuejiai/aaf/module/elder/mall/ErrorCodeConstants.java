package com.xuejiai.aaf.module.elder.mall;

import com.xuejiai.aaf.common.exception.ErrorCode;

/** 服务商城错误码，使用 10_200_000 ~ 10_299_999 段。 */
public interface ErrorCodeConstants {
    ErrorCode SERVICE_ITEM_NOT_FOUND = ErrorCode.of(10_200_000, 404, "服务项目不存在");
    ErrorCode SERVICE_ORDER_NOT_FOUND = ErrorCode.of(10_200_001, 404, "服务预约不存在");
    ErrorCode MALL_VERSION_CONFLICT = ErrorCode.of(10_200_002, 409, "服务商城数据已被其他请求修改，请刷新后重试");
    ErrorCode SERVICE_ITEM_INVALID = ErrorCode.of(10_200_003, "服务项目字段不合法");
    ErrorCode SERVICE_ORDER_INVALID = ErrorCode.of(10_200_004, "服务预约字段不合法");
    ErrorCode SERVICE_ORDER_STATUS_TRANSITION_INVALID =
            ErrorCode.of(10_200_005, 409, "服务预约状态不允许执行该操作");
    ErrorCode SERVICE_ITEM_UNAVAILABLE = ErrorCode.of(10_200_006, 409, "服务项目当前不可预约");
    ErrorCode SERVICE_BOOKING_FORBIDDEN = ErrorCode.of(10_200_007, 403, "当前账号未获代为预约授权");
}
