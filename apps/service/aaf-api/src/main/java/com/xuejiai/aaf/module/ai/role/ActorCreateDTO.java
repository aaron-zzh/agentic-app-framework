package com.xuejiai.aaf.module.ai.role;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** 创建 Actor Request DTO。 */
@Schema(description = "创建 Actor 请求")
public record ActorCreateDTO(
        @Schema(description = "Actor 标识", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank String actorId,
        @Schema(description = "名称", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank String name,
        @Schema(description = "人格描述") String persona,
        @Schema(description = "系统提示词") String systemPrompt,
        @Schema(description = "头像 URL") String avatarUrl) {}
