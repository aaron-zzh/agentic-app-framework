package com.xuejiai.aaf.module.ai.role;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

/** AI Role 信息 Response VO。 */
@Schema(description = "AI Role（能力配置）信息")
public record RoleVO(
        @Schema(description = "编号") Long id,
        @Schema(description = "Role 标识") String roleId,
        @Schema(description = "名称") String name,
        @Schema(description = "描述") String description,
        @Schema(description = "技能 ID 列表（JSON）") String skillIds,
        @Schema(description = "工具白名单（JSON）") String toolWhitelist,
        @Schema(description = "状态") String status,
        @Schema(description = "创建时间") LocalDateTime createTime,
        @Schema(description = "更新时间") LocalDateTime updateTime) {}
