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
        @Schema(description = "关联的 Actor ID") String actorId,
        @Schema(description = "关联的 Role ID") String roleId,
        @Schema(description = "记忆管道策略") String memoryStrategy,
        @Schema(description = "关联的知识库 ID") Long knowledgeBaseId,
        @Schema(description = "绑定的技能 ID 列表") List<String> skillIds,
        @Schema(description = "工具白名单") List<String> toolWhitelist) {}
