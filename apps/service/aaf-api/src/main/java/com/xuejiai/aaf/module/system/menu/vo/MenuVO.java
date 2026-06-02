package com.xuejiai.aaf.module.system.menu.vo;

import java.util.List;

/** 菜单树形 VO */
public record MenuVO(
        Long id,
        Long parentId,
        String title,
        String path,
        String icon,
        Integer sortOrder,
        Boolean visible,
        String menuType,
        String permissionCode,
        List<MenuVO> children) {}
