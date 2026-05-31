package com.xuejiai.aaf.module.system.permission.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/** 更新权限码请求。 */
@Schema(description = "更新权限码请求")
public record PermissionUpdateDTO(
        @Size(max = 100) @Schema(description = "权限名称") String name,
        @Size(max = 50) @Schema(description = "模块标识") String module,
        @Size(max = 50) @Schema(description = "资源标识") String resource,
        @Size(max = 50) @Schema(description = "动作标识") String action,
        @Size(max = 120) @Schema(description = "权限码") String code,
        @Schema(description = "状态") Integer status) {}
