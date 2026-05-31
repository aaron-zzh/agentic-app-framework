package com.xuejiai.aaf.module.system.permission.vo;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/** 权限码树形响应，按 module/resource 分组。 */
@Schema(description = "权限码树形结构")
public record PermissionTreeVO(
        @Schema(description = "主键 ID") Long id,
        @Schema(description = "权限名称") String name,
        @Schema(description = "权限编码") String code,
        @Schema(description = "模块标识") String module,
        @Schema(description = "资源标识") String resource,
        @Schema(description = "动作标识") String action,
        @Schema(description = "状态") Integer status,
        @Schema(description = "子节点") List<PermissionTreeVO> children) {}
