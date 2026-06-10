package com.xuejiai.aaf.module.ai.assistant.vo;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/** 可用助理树（含所有角色），供前端角色选择器分组展示。 */
@Schema(description = "可用助理（含角色列表）")
public record AssistantAvailableVO(
        @Schema(description = "Assistant ID") Long id,
        @Schema(description = "助理显示名称（Persona 名）") String name,
        @Schema(description = "头像 URL") String avatar,
        @Schema(description = "默认 Role ID") Long defaultRoleId,
        @Schema(description = "该助理下的所有角色") List<RoleItem> roles) {

    @Schema(description = "角色条目")
    public record RoleItem(
            @Schema(description = "Role ID") Long id,
            @Schema(description = "角色名称") String name,
            @Schema(description = "角色描述") String description) {}
}
