package com.xuejiai.aaf.module.ai.assistant.vo;

import java.time.LocalDateTime;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Assistant 信息响应 VO。
 *
 * @author AaronZZH & Kiro
 */
@Schema(description = "Assistant 信息")
public record AssistantVO(
        @Schema(description = "编号") Long id,
        @Schema(description = "所属用户 ID") Long userId,
        @Schema(description = "关联的 Persona ID") Long personaId,
        @Schema(description = "默认 Role ID") Long defaultRoleId,
        @Schema(description = "挂载的角色 ID 列表") List<Long> roleIds,
        @Schema(description = "记忆管道策略") String memoryStrategy,
        @Schema(description = "关联的知识库 ID") Long knowledgeBaseId,
        @Schema(description = "状态") String status,
        @Schema(description = "创建时间") LocalDateTime createTime,
        @Schema(description = "更新时间") LocalDateTime updateTime) {}
