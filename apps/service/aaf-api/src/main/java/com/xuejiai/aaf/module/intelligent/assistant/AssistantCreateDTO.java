package com.xuejiai.aaf.module.intelligent.assistant;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** 创建 Assistant Request DTO。 */
@Schema(description = "创建 Assistant 请求")
public record AssistantCreateDTO(
        @Schema(description = "Assistant 标识", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank String assistantId,
        @Schema(description = "Actor ID", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank String actorId,
        @Schema(description = "Role ID", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank String roleId,
        @Schema(description = "记忆策略", example = "HYBRID") String memoryStrategy,
        @Schema(description = "关联知识库 ID") Long knowledgeBaseId) {}
