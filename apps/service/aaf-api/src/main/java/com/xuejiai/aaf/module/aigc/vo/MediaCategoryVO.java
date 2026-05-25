package com.xuejiai.aaf.module.aigc.vo;

import java.util.List;

/** 素材分类 VO（树形结构）。 */
public record MediaCategoryVO(
        Long id,
        String name,
        Long parentId,
        Integer sortOrder,
        List<MediaCategoryVO> children) {}
