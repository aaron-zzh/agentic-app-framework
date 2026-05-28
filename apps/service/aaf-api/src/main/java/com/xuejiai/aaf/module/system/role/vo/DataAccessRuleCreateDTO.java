package com.xuejiai.aaf.module.system.role.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 创建数据权限规则请求。
 *
 * @author AaronZZH & Kiro
 */
public record DataAccessRuleCreateDTO(
        @NotBlank(message = "实体标识不能为空") @Size(max = 100) @Schema(description = "实体标识")
                String entitySlug,
        @NotBlank(message = "角色不能为空") String roles,
        @NotBlank(message = "条件不能为空") String condition,
        @Size(max = 10) String effect) {}
