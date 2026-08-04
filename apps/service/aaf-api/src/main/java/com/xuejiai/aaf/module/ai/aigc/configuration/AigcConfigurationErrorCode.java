package com.xuejiai.aaf.module.ai.aigc.configuration;

import com.xuejiai.aaf.common.exception.ErrorCode;

/** AIGC 配置子模块错误码。 */
public interface AigcConfigurationErrorCode {

    ErrorCode VERSION_IMMUTABLE = ErrorCode.of(7_001_000, "已发布配置不可原地修改，请创建新版本");
    ErrorCode PUBLISH_STATE_INVALID = ErrorCode.of(7_001_001, "只有草稿配置可以发布");
    ErrorCode VERSION_CONFLICT = ErrorCode.of(7_001_002, "配置已被其他请求修改，请刷新后重试");
    ErrorCode CONFIGURATION_NOT_FOUND = ErrorCode.of(7_001_003, "已发布配置不存在: {0}");
    ErrorCode CONFIGURATION_INCOMPATIBLE = ErrorCode.of(7_001_004, "配置组合不兼容: {0}");
}
