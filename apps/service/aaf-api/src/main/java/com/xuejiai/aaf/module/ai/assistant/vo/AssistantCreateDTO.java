package com.xuejiai.aaf.module.ai.assistant.vo;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * Assistant 创建请求 DTO。
 *
 * @author AaronZZH & Kiro
 */
@Schema(description = "创建 Assistant 请求")
public record AssistantCreateDTO(
        @Schema(description = "所属用户 ID", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull
                Long userId,
        @Schema(description = "关联的 Persona ID", requiredMode = Schema.RequiredMode.REQUIRED)
                @NotNull
                Long personaId,
        @Schema(description = "默认 Role ID", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull
                Long defaultRoleId,
        @Schema(description = "挂载的角色 ID 列表（助理可配多角色，未含 defaultRoleId 时自动补入）") List<Long> roleIds,
        @Schema(description = "记忆管道策略") String memoryStrategy,
        @Schema(description = "关联的知识库 ID") Long knowledgeBaseId) {}
