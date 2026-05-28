package com.xuejiai.aaf.module.system.permission.vo;

import com.xuejiai.aaf.module.system.permission.domain.PermissionType;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * 更新权限点请求。
 *
 * @author AaronZZH & Kiro
 */
@Schema(description = "更新权限点请求")
public record PermissionUpdateDTO(
        @Size(max = 100) @Schema(description = "权限名称") String name,
        @Schema(description = "权限类型") PermissionType type,
        @Schema(description = "父级 ID") Long parentId,
        @Schema(description = "路由路径") String path,
        @Schema(description = "图标") String icon,
        @Schema(description = "排序号") Integer sortOrder,
        @Schema(description = "状态") Integer status) {}
