package com.xuejiai.aaf.module.ai.role;

import io.swagger.v3.oas.annotations.media.Schema;

/** 调整绑定：排序、启停、设为默认；未传的字段保持不变。 */
@Schema(description = "调整助理角色绑定请求")
public record UpdateRoleBindingDTO(
        @Schema(description = "排序值，越小越靠前") Integer sortOrder,
        @Schema(description = "是否启用；默认角色不可停用") Boolean enabled,
        @Schema(description = "设为默认角色；只能设为 true，默认角色由切换产生") Boolean defaultRole) {}
