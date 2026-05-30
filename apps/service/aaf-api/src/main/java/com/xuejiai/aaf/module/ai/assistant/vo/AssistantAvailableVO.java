package com.xuejiai.aaf.module.ai.assistant.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/** 可用助理列表项（前端角色切换用）。 */
@Schema(description = "可用助理（含头像名称）")
public record AssistantAvailableVO(
        @Schema(description = "Assistant ID") String assistantId,
        @Schema(description = "Role ID") String roleId,
        @Schema(description = "Actor ID") String actorId,
        @Schema(description = "显示名称") String name,
        @Schema(description = "头像 URL") String avatar) {}
