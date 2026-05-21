package com.xuejiai.aaf.module.system.role.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 创建数据权限规则请求。 */
public record DataAccessRuleCreateDTO(
        @NotBlank(message = "实体标识不能为空") @Size(max = 100) String entitySlug,
        @NotBlank(message = "角色不能为空") String roles,
        @NotBlank(message = "条件不能为空") String condition,
        @Size(max = 10) String effect) {}
