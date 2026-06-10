package com.xuejiai.aaf.module.ai.role;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** 创建 AI Role Request DTO。 */
@Schema(description = "创建 AI Role 请求")
public record RoleCreateDTO(
        @Schema(description = "名称", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank
                String name,
        @Schema(description = "描述") String description,
        @Schema(description = "技能 ID 列表（JSON 数组）") String skillIds,
        @Schema(description = "工具授权池（JSON 数组）") String toolWhitelist) {}
