package com.xuejiai.aaf.module.ai.assistant;

import io.swagger.v3.oas.annotations.media.Schema;

/** 更新 Assistant Request DTO（null 字段不修改）。 */
@Schema(description = "更新 Assistant 请求")
public record AssistantUpdateDTO(
        @Schema(description = "Actor ID") String actorId,
        @Schema(description = "Role ID") String roleId,
        @Schema(description = "记忆策略") String memoryStrategy,
        @Schema(description = "关联知识库 ID") Long knowledgeBaseId) {}
