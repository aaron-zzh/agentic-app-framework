package com.xuejiai.aaf.module.system.permission.vo;

import com.xuejiai.aaf.module.system.permission.domain.PermissionType;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 创建权限点请求。
 *
 * @author AaronZZH & Kiro
 */
@Schema(description = "创建权限点请求")
public record PermissionCreateDTO(
        @NotBlank(message = "权限名称不能为空") @Size(max = 100) @Schema(description = "权限名称") String name,
        @NotBlank(message = "权限编码不能为空") @Size(max = 100) @Schema(description = "权限编码") String code,
        @NotNull(message = "权限类型不能为空") @Schema(description = "权限类型") PermissionType type,
        @Schema(description = "父级 ID，顶级传 0") Long parentId,
        @Schema(description = "路由路径") String path,
        @Schema(description = "图标") String icon,
        @Schema(description = "排序号") Integer sortOrder) {}
