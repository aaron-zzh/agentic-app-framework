package com.xuejiai.aaf.module.ai.assistant;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

/** Assistant 信息 Response VO。 */
@Schema(description = "Assistant 信息")
public record AssistantVO(
        @Schema(description = "编号") Long id,
        @Schema(description = "Assistant 标识") String assistantId,
        @Schema(description = "所属用户 ID") Long userId,
        @Schema(description = "Actor ID") String actorId,
        @Schema(description = "Role ID") String roleId,
        @Schema(description = "记忆策略") String memoryStrategy,
        @Schema(description = "关联知识库 ID") Long knowledgeBaseId,
        @Schema(description = "状态") String status,
        @Schema(description = "创建时间") LocalDateTime createTime,
        @Schema(description = "更新时间") LocalDateTime updateTime) {}
