package com.xuejiai.aaf.module.ai.assistant.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Assistant 创建请求 DTO。
 *
 * @author AaronZZH & Kiro
 */
@Schema(description = "创建 Assistant 请求")
public record AssistantCreateDTO(
        @Schema(description = "Assistant 唯一标识", requiredMode = Schema.RequiredMode.REQUIRED)
                @NotBlank
                String assistantId,
        @Schema(description = "所属用户 ID", requiredMode = Schema.RequiredMode.REQUIRED)
                @NotNull
                Long userId,
        @Schema(description = "关联的 Actor ID", requiredMode = Schema.RequiredMode.REQUIRED)
                @NotBlank
                String actorId,
        @Schema(description = "关联的 Role ID", requiredMode = Schema.RequiredMode.REQUIRED)
                @NotBlank
                String roleId,
        @Schema(description = "记忆管道策略") String memoryStrategy,
        @Schema(description = "关联的知识库 ID") Long knowledgeBaseId) {}
