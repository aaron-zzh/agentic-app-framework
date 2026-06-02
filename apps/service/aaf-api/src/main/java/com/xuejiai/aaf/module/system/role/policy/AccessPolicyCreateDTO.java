package com.xuejiai.aaf.module.system.role.policy;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** 创建/更新访问策略 Request DTO。 */
@Schema(description = "创建访问策略请求")
public record AccessPolicyCreateDTO(
        @Schema(description = "策略名称", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank
                String name,
        @Schema(description = "描述") String description,
        @Schema(description = "条件表达式（JSON）") String conditionJson,
        @Schema(description = "效果", example = "ALLOW") @NotBlank String effect,
        @Schema(description = "优先级", example = "100") Integer priority,
        @Schema(description = "目标资源类型") String targetResource,
        @Schema(description = "目标操作") String targetAction) {}
