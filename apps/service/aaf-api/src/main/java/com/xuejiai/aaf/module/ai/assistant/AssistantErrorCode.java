package com.xuejiai.aaf.module.ai.assistant;

import com.xuejiai.aaf.common.exception.ErrorCode;

/** Assistant 模块错误码。 */
public interface AssistantErrorCode {

    // 执行
    ErrorCode EXECUTION_EXPLICIT_KNOWLEDGE_IDS_REQUIRED =
            ErrorCode.of(7_003_000, "EXPLICIT knowledge 必须指定 knowledgeBaseIds");
    ErrorCode EXECUTION_KNOWLEDGE_IDS_REQUIRE_EXPLICIT_MODE =
            ErrorCode.of(7_003_001, "仅 EXPLICIT knowledge 可以指定 knowledgeBaseIds");
    ErrorCode EXECUTION_TEAM_OVERRIDE_FORBIDDEN =
            ErrorCode.of(7_003_002, "TEAM 模式不允许覆盖 Assistant、Role 或 Skill");
    ErrorCode EXECUTION_TEAM_OPTIONS_UNSUPPORTED =
            ErrorCode.of(7_003_003, "TEAM 模式仅支持 CONVERSATIONAL、AUTO 和 RETURN_ONLY");
    ErrorCode EXECUTION_TASK_JSON_OUTPUT_UNSUPPORTED =
            ErrorCode.of(7_003_004, "文案 TASK/FIXED 的规范输出必须是 text/markdown");
    ErrorCode EXECUTION_TEAM_ID_REQUIRED = ErrorCode.of(7_003_005, "teamId 不能为空");
    ErrorCode EXECUTION_INPUT_TEXT_REQUIRED = ErrorCode.of(7_003_006, "input.text 不能为空");
    ErrorCode EXECUTION_IMAGE_RESOLUTION_INCOMPLETE = ErrorCode.of(7_003_007, "图片材料解析结果不完整");
    ErrorCode EXECUTION_IMAGE_TYPE_INVALID = ErrorCode.of(7_003_008, "IMAGE 材料必须引用图片文件");
    ErrorCode EXECUTION_CONVERSATIONAL_RETURN_ONLY_REQUIRED =
            ErrorCode.of(7_003_009, "CONVERSATIONAL 执行仅支持 RETURN_ONLY");
    ErrorCode EXECUTION_CONVERSATIONAL_AUTO_ROUTE_REQUIRED =
            ErrorCode.of(7_003_010, "未指定 Role 和 Skill 的 CONVERSATIONAL 执行必须使用 AUTO Route");
    ErrorCode EXECUTION_CONVERSATIONAL_ROUTE_INCOMPLETE =
            ErrorCode.of(7_003_011, "显式对话路由必须同时指定 Role 和 Skill");
    ErrorCode EXECUTION_CONVERSATIONAL_FIXED_ROUTE_REQUIRED =
            ErrorCode.of(7_003_012, "显式 Role 和 Skill 的 CONVERSATIONAL 执行必须使用 FIXED Route");
    ErrorCode EXECUTION_ROUTE_ROLE_NOT_FOUND = ErrorCode.of(7_003_013, "请求 Role 不属于当前 Assistant");
    ErrorCode EXECUTION_ROUTE_SKILL_NOT_AVAILABLE =
            ErrorCode.of(7_003_014, "FIXED Route 只能引用当前 Scope 的 ON_DEMAND Skill");
    ErrorCode EXECUTION_TASK_FIXED_ROUTE_REQUIRED =
            ErrorCode.of(7_003_015, "TASK 执行必须使用 FIXED Route");
    ErrorCode EXECUTION_TASK_ROUTE_INCOMPLETE =
            ErrorCode.of(7_003_016, "TASK/FIXED 必须指定 Role 和 Skill");
    ErrorCode EXECUTION_CONTROLLED_CONTEXT_SOURCE_FORBIDDEN =
            ErrorCode.of(7_003_017, "MEMORY 和 KNOWLEDGE 上下文必须由执行期 L1 授权检索");
    ErrorCode EXECUTION_DEFAULT_ASSISTANT_NOT_FOUND =
            ErrorCode.of(7_003_018, 404, "当前认证用户没有可用的默认 Assistant");
    ErrorCode EXECUTION_ASSISTANT_NOT_FOUND = ErrorCode.of(7_003_019, 404, "Assistant 定义不存在");
    ErrorCode EXECUTION_ASSISTANT_NOT_EXECUTABLE = ErrorCode.of(7_003_020, "Assistant 定义不可执行");
    ErrorCode EXECUTION_TEAM_VERSION_INVALID = ErrorCode.of(7_003_021, "teamVersion 必须大于 0");
    ErrorCode EXECUTION_TEAM_NOT_FOUND = ErrorCode.of(7_003_022, 404, "Team 定义版本不存在");
    ErrorCode EXECUTION_TEAM_NOT_EXECUTABLE = ErrorCode.of(7_003_023, "Team 定义版本不可执行");
    ErrorCode EXECUTION_TEAM_WORKER_KEY_DUPLICATE =
            ErrorCode.of(7_003_024, "Team Worker memberKey 重复");
    ErrorCode EXECUTION_TEAM_MEMBER_ASSISTANT_NOT_FOUND =
            ErrorCode.of(7_003_025, 404, "Team 成员 Assistant 定义不存在");
    ErrorCode EXECUTION_TEAM_MEMBER_REVISION_INVALID =
            ErrorCode.of(7_003_026, "Team 成员 Assistant revision 不可执行");
    ErrorCode EXECUTION_TEAM_MEMBER_ROLE_NOT_FOUND =
            ErrorCode.of(7_003_027, "Team 成员 Role 不属于 Assistant");
    ErrorCode EXECUTION_TEAM_MEMBER_SKILL_NOT_AVAILABLE =
            ErrorCode.of(7_003_028, "Team 成员 Skill 不在可用范围内");
    ErrorCode EXECUTION_TEAM_MEMBER_TOOL_SCOPE_INVALID =
            ErrorCode.of(7_003_029, "Team 成员工具权限超出 Assistant 与 Role 联合白名单");
    ErrorCode EXECUTION_OUTPUT_MAX_LENGTH_INVALID =
            ErrorCode.of(7_003_030, "output.maxCharLen 必须在允许范围内");
    ErrorCode EXECUTION_OUTPUT_FORMAT_UNSUPPORTED =
            ErrorCode.of(7_003_031, "output.format 当前仅支持 JSON 严格验证");
    ErrorCode EXECUTION_SPEC_REQUIRED = ErrorCode.of(7_003_032, "执行规格不能为空");
    ErrorCode EXECUTION_ASSISTANT_ID_REQUIRED = ErrorCode.of(7_003_033, "assistantId 不能为空");
    ErrorCode EXECUTION_INPUT_REQUIRED = ErrorCode.of(7_003_034, "input 不能为空");
    ErrorCode EXECUTION_KNOWLEDGE_BASE_LIMIT_EXCEEDED =
            ErrorCode.of(7_003_035, "knowledgeBaseIds 最多允许 20 个");
    ErrorCode EXECUTION_TOP_K_OUT_OF_RANGE = ErrorCode.of(7_003_036, "topK 必须在 1 到 20 之间");
    ErrorCode EXECUTION_SIMILARITY_THRESHOLD_OUT_OF_RANGE =
            ErrorCode.of(7_003_037, "similarityThreshold 必须在 0 到 1 之间");
    ErrorCode EXECUTION_MODEL_SELECTION_REQUIRED = ErrorCode.of(7_003_038, "modelSelection 不能为空");
    ErrorCode EXECUTION_MEMORY_MODE_REQUIRED = ErrorCode.of(7_003_039, "memoryMode 不能为空");
    ErrorCode EXECUTION_INPUT_VARIABLE_LIMIT_EXCEEDED =
            ErrorCode.of(7_003_040, "input.variables 最多允许 100 个变量");
    ErrorCode EXECUTION_INPUT_VARIABLE_NAME_REQUIRED =
            ErrorCode.of(7_003_041, "input.variables 变量名不能为空");
    ErrorCode EXECUTION_INPUT_VARIABLES_NOT_SERIALIZABLE =
            ErrorCode.of(7_003_042, "input.variables 必须可序列化为 JSON");
    ErrorCode EXECUTION_ATTACHMENT_TYPE_REQUIRED =
            ErrorCode.of(7_003_043, "input.attachments 的 type 不能为空");
    ErrorCode EXECUTION_TEXT_ATTACHMENT_RESOURCE_FORBIDDEN =
            ErrorCode.of(7_003_044, "TEXT 附件不允许设置 resourceId");
    ErrorCode EXECUTION_TEXT_ATTACHMENT_CONTENT_REQUIRED =
            ErrorCode.of(7_003_045, "TEXT 附件 content 不能为空");
    ErrorCode EXECUTION_IMAGE_ATTACHMENT_CONTENT_FORBIDDEN =
            ErrorCode.of(7_003_046, "IMAGE 附件不允许设置 content");
    ErrorCode EXECUTION_IMAGE_ATTACHMENT_RESOURCE_REQUIRED =
            ErrorCode.of(7_003_047, "IMAGE 附件 resourceId 不能为空");
    ErrorCode EXECUTION_MODEL_REQUIRED = ErrorCode.of(7_003_048, "model 不能为空");
    ErrorCode EXECUTION_MODEL_MODE_REQUIRED = ErrorCode.of(7_003_049, "model.mode 不能为空");
    ErrorCode EXECUTION_AUTO_MODEL_ID_FORBIDDEN =
            ErrorCode.of(7_003_050, "AUTO model 不允许指定 modelId");
    ErrorCode EXECUTION_EXPLICIT_MODEL_ID_REQUIRED =
            ErrorCode.of(7_003_051, "EXPLICIT model 必须指定 modelId");
    ErrorCode EXECUTION_KNOWLEDGE_BASE_ID_REQUIRED =
            ErrorCode.of(7_003_052, "knowledgeBaseIds 不能包含 null");
    ErrorCode EXECUTION_IMAGE_FILE_KEY_REQUIRED = ErrorCode.of(7_003_053, "图片 fileKey 不能为空");
    ErrorCode EXECUTION_EVENTS_REQUIRED = ErrorCode.of(7_003_054, "events 不能为空");
    ErrorCode EXECUTION_INTENT_REQUIRED = ErrorCode.of(7_003_055, "executionIntent 不能为空");
    ErrorCode EXECUTION_MATERIAL_NAME_REQUIRED = ErrorCode.of(7_003_056, "材料名称不能为空");
    ErrorCode EXECUTION_MATERIAL_CONTENT_REQUIRED = ErrorCode.of(7_003_057, "材料内容不能为空");
    ErrorCode EXECUTION_CONTEXT_SOURCE_TYPE_REQUIRED =
            ErrorCode.of(7_003_058, "受控上下文 sourceType 不能为空");
    ErrorCode EXECUTION_CONTEXT_SOURCE_KEY_REQUIRED =
            ErrorCode.of(7_003_059, "受控上下文 sourceKey 不能为空");
    ErrorCode EXECUTION_CONTEXT_VERSION_REQUIRED = ErrorCode.of(7_003_060, "受控上下文 version 不能为空");
    ErrorCode EXECUTION_CONTEXT_SCOPE_REQUIRED = ErrorCode.of(7_003_061, "受控上下文 scope 不能为空");
    ErrorCode EXECUTION_CONTEXT_REASON_REQUIRED = ErrorCode.of(7_003_062, "受控上下文 reason 不能为空");
    ErrorCode EXECUTION_CONTEXT_TEXT_REQUIRED = ErrorCode.of(7_003_063, "受控上下文 text 不能为空");
    ErrorCode EXECUTION_THREAD_ID_REQUIRED = ErrorCode.of(7_003_064, "threadId 不能为空白");
    ErrorCode EXECUTION_THREAD_ID_TOO_LONG = ErrorCode.of(7_003_065, "threadId 长度不能超过 128");
    ErrorCode EXECUTION_RUN_ID_REQUIRED = ErrorCode.of(7_003_066, "runId 不能为空白");
    ErrorCode EXECUTION_RUN_ID_TOO_LONG = ErrorCode.of(7_003_067, "runId 长度不能超过 128");
    ErrorCode EXECUTION_ID_REQUIRED = ErrorCode.of(7_003_068, "executionId 不能为空");

    // 审批
    ErrorCode APPROVAL_RECOVERY_REQUIRES_APPROVED = ErrorCode.of(7_003_100, "仅批准决定可恢复执行");
    ErrorCode APPROVAL_NOT_FOUND = ErrorCode.of(7_003_101, 404, "Assistant 审批不存在");

    // 委托任务
    ErrorCode DELEGATED_TASK_NOT_FOUND = ErrorCode.of(7_003_200, 404, "Assistant 委托任务不存在");
}
