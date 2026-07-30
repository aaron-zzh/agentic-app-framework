package com.xuejiai.aaf.module.content;

import com.xuejiai.aaf.common.exception.ErrorCode;

/**
 * Content Studio 错误码，使用 8_000_000 ~ 8_999_999 段。
 *
 * @author AaronZZH & Kiro
 */
public interface ErrorCodeConstants {

    ErrorCode CONTENT_PROJECT_NOT_FOUND = ErrorCode.of(8_000_000, "内容项目不存在");
    ErrorCode CONTENT_PROJECT_TYPE_NOT_FOUND = ErrorCode.of(8_000_001, "项目类型不存在: {0}");
    ErrorCode CONTENT_BLUEPRINT_NOT_FOUND = ErrorCode.of(8_000_002, "项目蓝图不存在");
    ErrorCode CONTENT_BLUEPRINT_INCOMPATIBLE = ErrorCode.of(8_000_003, "蓝图与生产模式不兼容: {0}");
    ErrorCode CONTENT_DOMAIN_EXTENSION_NOT_FOUND = ErrorCode.of(8_000_004, "行业扩展不存在");
    ErrorCode CONTENT_DOMAIN_EXTENSION_NOT_PUBLISHED = ErrorCode.of(8_000_005, "行业扩展未发布，不能用于新项目");
    ErrorCode CONTENT_CHANNEL_SPEC_NOT_FOUND = ErrorCode.of(8_000_006, "渠道规格不存在: {0}");
    ErrorCode CONTENT_BRAND_PROFILE_NOT_FOUND = ErrorCode.of(8_000_007, "品牌/IP 资料不存在");
    ErrorCode CONTENT_BRAND_PROFILE_REQUIRED = ErrorCode.of(8_000_008, "必须指定主品牌/IP 资料");
    ErrorCode CONTENT_OBJECT_NOT_FOUND = ErrorCode.of(8_000_009, "项目对象不存在");
    ErrorCode CONTENT_OBJECT_PARENT_INVALID = ErrorCode.of(8_000_010, "父级对象无效");
    ErrorCode CONTENT_OBJECT_TYPE_INVALID = ErrorCode.of(8_000_011, "不支持的对象类型: {0}");
    ErrorCode CONTENT_RELATION_NOT_FOUND = ErrorCode.of(8_000_012, "项目关系不存在");
    ErrorCode CONTENT_RELATION_SELF_LOOP = ErrorCode.of(8_000_013, "关系两端不能是同一对象");
    ErrorCode CONTENT_RELATION_CROSS_PROJECT = ErrorCode.of(8_000_014, "关系两端必须属于同一项目");
    ErrorCode CONTENT_RELATION_DUPLICATE = ErrorCode.of(8_000_015, "关系已存在");
    ErrorCode CONTENT_PROJECT_STATUS_INVALID = ErrorCode.of(8_000_016, "不支持的项目状态: {0}");
    ErrorCode CONTENT_PROJECT_ARCHIVED_READONLY = ErrorCode.of(8_000_017, "已归档项目只读");
    ErrorCode CONTENT_EXECUTION_RUN_NOT_FOUND = ErrorCode.of(8_000_018, "执行记录不存在");
    ErrorCode CONTENT_EXECUTION_TARGET_INVALID = ErrorCode.of(8_000_019, "执行目标无效");
    ErrorCode CONTENT_HARD_RULE_VIOLATION = ErrorCode.of(8_000_020, "硬规则校验未通过: {0}");
    ErrorCode CONTENT_BUDGET_EXCEEDED = ErrorCode.of(8_000_021, "超出项目预算上限");
    ErrorCode CONTENT_CONFIRMATION_REQUIRED = ErrorCode.of(8_000_022, "该动作需要人工确认");
    ErrorCode CONTENT_SNIPPET_NOT_FOUND = ErrorCode.of(8_000_023, "片段不存在");
    ErrorCode CONTENT_GRAPH_VERSION_CONFLICT = ErrorCode.of(8_000_024, "项目图谱已被其他请求修改，请刷新后重试");
    ErrorCode CONTENT_ACTION_NOT_ALLOWED = ErrorCode.of(8_000_025, "当前项目不允许该动作: {0}");
    ErrorCode CONTENT_ACTION_BINDING_NOT_FOUND = ErrorCode.of(8_000_026, "未找到动作 {0} 的执行绑定");
    ErrorCode CONTENT_OBJECT_VERSION_NOT_FOUND = ErrorCode.of(8_000_027, "对象版本不存在");
    ErrorCode CONTENT_OBJECT_VERSION_NOT_CANDIDATE = ErrorCode.of(8_000_028, "只有候选版本可以采用");
    ErrorCode CONTENT_EXECUTION_NOT_CANCELABLE = ErrorCode.of(8_000_029, "当前状态不可取消");
    ErrorCode CONTENT_EXECUTION_NOT_RETRYABLE = ErrorCode.of(8_000_030, "当前状态不可重试");
}
