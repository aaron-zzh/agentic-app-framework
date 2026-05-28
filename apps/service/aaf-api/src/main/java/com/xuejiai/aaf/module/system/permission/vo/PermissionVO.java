package com.xuejiai.aaf.module.system.permission.vo;

import java.time.LocalDateTime;

import com.xuejiai.aaf.module.system.permission.domain.PermissionType;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 权限点响应。
 *
 * @author AaronZZH & Kiro
 */
@Schema(description = "权限点信息")
public record PermissionVO(
        @Schema(description = "主键 ID") Long id,
        @Schema(description = "权限名称") String name,
        @Schema(description = "权限编码") String code,
        @Schema(description = "权限类型") PermissionType type,
        @Schema(description = "父级 ID") Long parentId,
        @Schema(description = "路由路径") String path,
        @Schema(description = "图标") String icon,
        @Schema(description = "排序号") Integer sortOrder,
        @Schema(description = "状态") Integer status,
        @Schema(description = "创建时间") LocalDateTime createTime) {}
