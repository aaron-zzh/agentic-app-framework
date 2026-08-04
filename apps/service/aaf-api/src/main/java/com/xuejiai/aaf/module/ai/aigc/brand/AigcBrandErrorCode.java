package com.xuejiai.aaf.module.ai.aigc.brand;

import com.xuejiai.aaf.common.exception.ErrorCode;

/** AIGC brand 子模块错误码。 */
public interface AigcBrandErrorCode {

    ErrorCode PROFILE_NOT_FOUND = ErrorCode.of(7_002_000, "品牌/IP 资料不存在");
    ErrorCode VERSION_NOT_FOUND = ErrorCode.of(7_002_001, "品牌/IP 资料版本不存在");
    ErrorCode VERSION_IMMUTABLE = ErrorCode.of(7_002_002, "已发布品牌资料版本不可改写");
    ErrorCode VERSION_CONFLICT = ErrorCode.of(7_002_003, "品牌资料已被其他请求修改，请刷新后重试");
    ErrorCode PROFILE_HAS_VERSIONS = ErrorCode.of(7_002_004, "品牌资料已有版本，不能删除");
}
