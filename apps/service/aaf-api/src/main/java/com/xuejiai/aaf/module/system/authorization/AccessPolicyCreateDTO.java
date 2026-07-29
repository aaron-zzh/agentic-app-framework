package com.xuejiai.aaf.module.system.authorization;

import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "创建或更新访问策略草稿")
public record AccessPolicyCreateDTO(
        @Schema(description = "策略名称", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank
                String name,
        @Schema(description = "描述") String description,
        @Schema(description = "安全 JSON 条件，不接受 SpEL") @NotBlank String conditionJson,
        @Schema(description = "attributes.* 事实类型白名单") Map<String, String> factSchema,
        @Schema(description = "效果：ALLOW/DENY/CHALLENGE", example = "ALLOW") @NotBlank String effect,
        @Schema(description = "优先级", example = "100") Integer priority,
        @Schema(description = "目标资源类型") @NotBlank String targetResource,
        @Schema(description = "目标操作") @NotBlank String targetAction) {}
