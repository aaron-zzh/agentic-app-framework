package com.xuejiai.aaf.module.system.role.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 创建角色请求。
 *
 * @author AaronZZH & Kiro
 */
public record RoleCreateDTO(
        @NotBlank(message = "角色编码不能为空") @Size(max = 50) @Schema(description = "编码") String code,
        @NotBlank(message = "角色名称不能为空") @Size(max = 100) @Schema(description = "名称") String name,
        @Size(max = 500) @Schema(description = "描述") String description) {}
