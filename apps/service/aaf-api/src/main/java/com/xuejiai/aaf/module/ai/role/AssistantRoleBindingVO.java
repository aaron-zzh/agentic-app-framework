package com.xuejiai.aaf.module.ai.role;

import io.swagger.v3.oas.annotations.media.Schema;

/** 助理与角色的绑定信息。角色已下架或对所有者不可见时 {@code role} 为空、{@code usable} 为 false。 */
@Schema(description = "助理角色绑定")
public record AssistantRoleBindingVO(
        @Schema(description = "角色 ID") Long roleId,
        @Schema(description = "角色；已不可见时为空") RoleVO role,
        @Schema(description = "绑定是否启用") boolean enabled,
        @Schema(description = "是否为默认角色") boolean defaultRole,
        @Schema(description = "排序值，越小越靠前") int sortOrder,
        @Schema(description = "角色当前是否可用于执行") boolean usable) {}
