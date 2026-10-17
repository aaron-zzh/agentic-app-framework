package com.xuejiai.aaf.module.device;

import com.xuejiai.aaf.common.exception.ErrorCode;

/** 设备通用层错误码，使用 11_000_000 ~ 11_099_999 段。 */
public interface ErrorCodeConstants {
    ErrorCode DEVICE_NOT_FOUND = ErrorCode.of(11_000_000, 404, "设备不存在");
    ErrorCode DEVICE_MESSAGE_NOT_FOUND = ErrorCode.of(11_000_001, 404, "设备消息不存在");
    ErrorCode DEVICE_ALREADY_BOUND = ErrorCode.of(11_000_002, 409, "设备已绑定");
    ErrorCode DEVICE_CREDENTIAL_INVALID = ErrorCode.of(11_000_003, 401, "设备凭据无效");
    ErrorCode DEVICE_CREDENTIAL_REVOKED = ErrorCode.of(11_000_004, 401, "设备凭据已撤销");
    ErrorCode DEVICE_MESSAGE_STATUS_TRANSITION_INVALID =
            ErrorCode.of(11_000_005, "设备消息状态不允许从 {0} 变更为 {1}");
    ErrorCode DEVICE_VERSION_CONFLICT = ErrorCode.of(11_000_006, 409, "设备数据已被其他请求修改，请刷新后重试");
    ErrorCode DEVICE_MESSAGE_IDEMPOTENCY_CONFLICT =
            ErrorCode.of(11_000_007, 409, "同一 commandId 不能绑定不同指令内容");
    ErrorCode DEVICE_MESSAGE_INVALID = ErrorCode.of(11_000_008, 400, "设备消息格式无效");
    ErrorCode MQTT_CHANNEL_UNAVAILABLE = ErrorCode.of(11_000_009, 503, "MQTT 设备通道不可用");
    ErrorCode EMQX_CALLBACK_UNAUTHORIZED = ErrorCode.of(11_000_010, 401, "EMQX 回调认证失败");
    ErrorCode DEVICE_PAIRING_INVALID = ErrorCode.of(11_000_011, 400, "配对码无效或已使用");
    ErrorCode DEVICE_PAIRING_EXPIRED = ErrorCode.of(11_000_012, 410, "设备配对会话已过期");
    ErrorCode DEVICE_PAIRING_LOCKED = ErrorCode.of(11_000_013, 429, "配对失败次数过多，请稍后重试");
    ErrorCode DEVICE_PAIRING_RATE_LIMITED = ErrorCode.of(11_000_014, 429, "设备配对请求过于频繁");
    ErrorCode DEVICE_PAIRING_REQUEST_INVALID = ErrorCode.of(11_000_015, 400, "设备配对请求无效");
    ErrorCode DEVICE_REQUEST_INVALID = ErrorCode.of(11_000_016, 400, "设备数据无效");
    ErrorCode DEVICE_NOT_BOUND = ErrorCode.of(11_000_017, 409, "设备未处于绑定状态");
    ErrorCode DEVICE_OWNER_CREDITS_INSUFFICIENT =
            ErrorCode.of(11_000_018, 402, "设备所有者积分不足，请回退本机大脑");
    ErrorCode DEVICE_VOICE_GATEWAY_FAILED = ErrorCode.of(11_000_019, 503, "设备语音网关暂不可用，请回退本机大脑");
    ErrorCode DEVICE_SCENARIO_NOT_FOUND = ErrorCode.of(11_000_020, 404, "设备场景不存在: {0}");
    ErrorCode DEVICE_SCENARIO_DISABLED = ErrorCode.of(11_000_021, 403, "当前组织未启用设备场景: {0}");
    ErrorCode DEVICE_SCENARIO_BINDING_INVALID = ErrorCode.of(11_000_022, 400, "设备场景主体绑定无效");
    ErrorCode DEVICE_ROLE_UNAVAILABLE = ErrorCode.of(11_000_023, 409, "设备没有可用角色");
    ErrorCode DEVICE_SCENARIO_SUBJECT_REQUIRED =
            ErrorCode.of(11_000_024, 400, "场景「{0}」需要先选择场景主体：{1}");
    ErrorCode DEVICE_SCENARIO_SUBJECT_UNSUPPORTED =
            ErrorCode.of(11_000_025, 400, "场景「{0}」不需要场景主体，请清空场景主体后再保存");
}
