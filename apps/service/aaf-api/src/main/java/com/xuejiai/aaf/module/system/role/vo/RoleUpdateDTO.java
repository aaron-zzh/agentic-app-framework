package com.xuejiai.aaf.module.system.role.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * 更新角色请求。
 *
 * @author AaronZZH & Kiro
 */
public record RoleUpdateDTO(
        @Size(max = 100) @Schema(description = "名称") String name,
        @Size(max = 500) String description,
        Integer status) {}
