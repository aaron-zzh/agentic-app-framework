package com.xuejiai.aaf.module.ai.assistant.vo;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Assistant 更新请求 DTO。
 *
 * @author AaronZZH & Kiro
 */
@Schema(description = "更新 Assistant 请求")
public record AssistantUpdateDTO(
        @Schema(description = "关联的 Persona ID") Long personaId,
        @Schema(description = "默认 Role ID") Long defaultRoleId,
        @Schema(description = "挂载的角色 ID 列表（传入则全量替换关联）") List<Long> roleIds,
        @Schema(description = "记忆管道策略") String memoryStrategy,
        @Schema(description = "关联的知识库 ID") Long knowledgeBaseId) {}
