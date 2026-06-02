package com.xuejiai.aaf.module.system.menu.vo;

import jakarta.validation.constraints.NotBlank;

/** 菜单创建 DTO */
public record MenuCreateDTO(
        @NotBlank String title,
        Long parentId,
        String path,
        String icon,
        Integer sortOrder,
        Boolean visible,
        String menuType,
        String permissionCode) {}
