package com.xuejiai.aaf.module.system.permission.vo;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

/** 权限码响应。 */
@Schema(description = "权限码信息")
public record PermissionVO(
        @Schema(description = "主键 ID") Long id,
        @Schema(description = "权限名称") String name,
        @Schema(description = "权限编码") String code,
        @Schema(description = "模块标识") String module,
        @Schema(description = "资源标识") String resource,
        @Schema(description = "动作标识") String action,
        @Schema(description = "状态") Integer status,
        @Schema(description = "创建时间") LocalDateTime createTime) {}
