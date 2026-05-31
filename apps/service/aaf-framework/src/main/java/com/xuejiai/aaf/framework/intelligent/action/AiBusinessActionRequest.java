package com.xuejiai.aaf.framework.intelligent.action;

import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;

/** AI 业务动作调用请求。 */
public record AiBusinessActionRequest(
        @Schema(description = "动作标识，如 entity.query / entity.detail / entity.create")
                String action,
        @Schema(description = "实体标识，如 system-role") String entity,
        @Schema(description = "AI 会话 ID，用于人工确认后恢复执行") String sessionId,
        @Schema(description = "AI 对本次动作参数与意图匹配的置信度，0.0-1.0") Double confidence,
        @Schema(description = "本次动作是否可自动验证") Boolean verifiable,
        @Schema(description = "动作参数") Map<String, Object> params) {}
