package com.xuejiai.aaf.module.system.role.vo;

import java.time.LocalDateTime;

/** 角色响应。 */
public record RoleVO(
        Long id,
        String code,
        String name,
        String description,
        Integer status,
        LocalDateTime createTime) {}
