package com.xuejiai.aaf.module.ai.role;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/** 角色库：当前用户可绑定的角色，标注是否已绑定到指定助理，以及绑定数量与上限。 */
@Schema(description = "角色库")
public record RoleLibraryVO(
        @Schema(description = "标注绑定关系所对应的助理 ID；用户没有默认助理时为空") Long assistantId,
        @Schema(description = "该助理已绑定的角色总数") int boundCount,
        @Schema(description = "绑定上限，来自系统参数 ai.assistant_max_roles") int maxRoles,
        @Schema(description = "SYSTEM、PUBLIC 与自己的 PRIVATE 角色（仅 active）") List<Item> items) {

    public RoleLibraryVO {
        items = List.copyOf(items);
    }

    /** 角色库条目。 */
    @Schema(description = "角色库条目")
    public record Item(
            @Schema(description = "角色") RoleVO role,
            @Schema(description = "是否已绑定到该助理") boolean bound,
            @Schema(description = "绑定是否启用") boolean enabled,
            @Schema(description = "是否为该助理的默认角色") boolean defaultRole) {}
}
