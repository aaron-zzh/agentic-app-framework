package com.xuejiai.aaf.module.ai.role;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** 绑定角色请求。 */
@Schema(description = "绑定角色请求")
public record BindRoleDTO(
        @Schema(description = "角色 ID", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull
                Long roleId) {}
