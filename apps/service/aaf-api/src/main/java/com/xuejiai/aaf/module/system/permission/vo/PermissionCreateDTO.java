package com.xuejiai.aaf.module.system.permission.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 创建权限码请求。 */
@Schema(description = "创建权限码请求")
public record PermissionCreateDTO(
        @NotBlank(message = "权限名称不能为空") @Size(max = 100) @Schema(description = "权限名称") String name,
        @NotBlank(message = "模块不能为空") @Size(max = 50) @Schema(description = "模块标识") String module,
        @NotBlank(message = "资源不能为空") @Size(max = 50) @Schema(description = "资源标识") String resource,
        @NotBlank(message = "动作不能为空") @Size(max = 50) @Schema(description = "动作标识") String action,
        @Size(max = 120) @Schema(description = "权限码；为空时按 模块:资源:动作 自动生成") String code) {}
