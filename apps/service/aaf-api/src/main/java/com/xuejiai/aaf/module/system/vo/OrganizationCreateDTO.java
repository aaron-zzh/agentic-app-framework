package com.xuejiai.aaf.module.system.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 创建组织请求。 */
public record OrganizationCreateDTO(
        @NotBlank(message = "组织名称不能为空") @Size(max = 100) String name,
        @NotBlank(message = "组织标识不能为空") @Size(max = 100) String slug) {}
