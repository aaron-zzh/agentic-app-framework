package com.xuejiai.aaf.module.ai.role;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

/** Persona 信息 Response VO。 */
@Schema(description = "Persona（人格）信息")
public record PersonaVO(
        @Schema(description = "编号") Long id,
        @Schema(description = "名称") String name,
        @Schema(description = "人格描述") String persona,
        @Schema(description = "系统提示词") String systemPrompt,
        @Schema(description = "头像 URL") String avatarUrl,
        @Schema(description = "状态") String status,
        @Schema(description = "创建时间") LocalDateTime createTime,
        @Schema(description = "更新时间") LocalDateTime updateTime) {}
