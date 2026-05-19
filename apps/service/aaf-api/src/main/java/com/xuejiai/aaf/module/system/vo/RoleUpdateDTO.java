package com.xuejiai.aaf.module.system.vo;

import jakarta.validation.constraints.Size;

/** 更新角色请求。 */
public record RoleUpdateDTO(
        @Size(max = 100) String name, @Size(max = 500) String description, Integer status) {}
