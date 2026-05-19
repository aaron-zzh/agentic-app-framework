package com.xuejiai.aaf.module.system.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 创建角色请求。 */
public record RoleCreateDTO(
        @NotBlank(message = "角色编码不能为空") @Size(max = 50) String code,
        @NotBlank(message = "角色名称不能为空") @Size(max = 100) String name,
        @Size(max = 500) String description) {}
