package com.xuejiai.aaf.module.digitalhuman;

import com.xuejiai.aaf.common.exception.ErrorCode;

/**
 * 数字人模块错误码，使用 8_000_000 ~ 8_999_999 段。
 *
 * <p>子模块分段：
 *
 * <ul>
 *   <li>DIGITAL_HUMAN：8_000_000 ~ 8_000_999
 *   <li>PRESET_VOICE：8_001_000 ~ 8_001_999
 *   <li>VIDEO：8_002_000 ~ 8_002_999
 *   <li>CREDIT：8_003_000 ~ 8_003_999
 * </ul>
 */
public interface ErrorCodeConstants {

    // ========== DIGITAL_HUMAN 子模块 8_000_000 ==========
    ErrorCode DIGITAL_HUMAN_NOT_FOUND = ErrorCode.of(8_000_000, 404, "数字人不存在");
    ErrorCode DIGITAL_HUMAN_NAME_REQUIRED = ErrorCode.of(8_000_001, "请输入数字人名称");
    ErrorCode DIGITAL_HUMAN_AVATAR_REQUIRED = ErrorCode.of(8_000_002, "请上传数字人形象");
    ErrorCode DIGITAL_HUMAN_VOICE_CONFIG_REQUIRED = ErrorCode.of(8_000_003, "请选择音色配置");
    ErrorCode DIGITAL_HUMAN_STATUS_INVALID = ErrorCode.of(8_000_004, "数字人状态不正确");
    ErrorCode DIGITAL_HUMAN_ARCHIVED_CANNOT_GENERATE = ErrorCode.of(8_000_005, "已归档的数字人无法生成视频");
    ErrorCode DIGITAL_HUMAN_VERSION_CONFLICT = ErrorCode.of(8_000_006, 409, "数字人信息已更新，请刷新后重试");

    // ========== PRESET_VOICE 子模块 8_001_000 ==========
    ErrorCode PRESET_VOICE_NOT_FOUND = ErrorCode.of(8_001_000, 404, "音色不存在");
    ErrorCode PRESET_VOICE_DISABLED = ErrorCode.of(8_001_001, "该音色暂不可用");
    ErrorCode PRESET_VOICE_PROVIDER_REQUIRED = ErrorCode.of(8_001_002, "请选择音色供应商");
    ErrorCode PRESET_VOICE_ID_REQUIRED = ErrorCode.of(8_001_003, "请选择音色");

    // ========== VIDEO 子模块 8_002_000 ==========
    ErrorCode VIDEO_NOT_FOUND = ErrorCode.of(8_002_000, 404, "视频不存在");
    ErrorCode VIDEO_SCRIPT_REQUIRED = ErrorCode.of(8_002_001, "请输入视频脚本");
    ErrorCode VIDEO_SCRIPT_TOO_LONG = ErrorCode.of(8_002_002, "视频脚本不能超过 {0} 字");
    ErrorCode VIDEO_DURATION_INVALID = ErrorCode.of(8_002_003, "视频时长设置不正确");
    ErrorCode VIDEO_SCENE_EMPTY = ErrorCode.of(8_002_004, "请至少添加一个场景");
    ErrorCode VIDEO_SCENE_CONTENT_REQUIRED = ErrorCode.of(8_002_005, "场景 {0} 的内容不能为空");
    ErrorCode VIDEO_GENERATION_FAILED = ErrorCode.of(8_002_006, "视频生成失败: {0}");
    ErrorCode VIDEO_PROVIDER_INVALID = ErrorCode.of(8_002_007, "不支持的视频提供商: {0}");
    ErrorCode VIDEO_DELETE_FORBIDDEN = ErrorCode.of(8_002_008, "无权删除该视频");

    // ========== CREDIT 子模块 8_003_000 ==========
    ErrorCode CREDIT_INSUFFICIENT = ErrorCode.of(8_003_000, "积分不足，需要 {0} 积分，当前仅有 {1} 积分");
    ErrorCode CREDIT_CALCULATION_FAILED = ErrorCode.of(8_003_001, "积分计算失败");
    ErrorCode CREDIT_DEDUCTION_FAILED = ErrorCode.of(8_003_002, "积分扣减失败");
    ErrorCode CREDIT_REFUND_FAILED = ErrorCode.of(8_003_003, "积分退还失败");
    ErrorCode CREDIT_TRANSACTION_NOT_FOUND = ErrorCode.of(8_003_004, 404, "积分交易记录不存在");
}
