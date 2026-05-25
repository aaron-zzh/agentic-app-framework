package com.xuejiai.aaf.module.aigc.vo;

/** 素材更新请求。 */
public record MediaAssetUpdateDTO(
        String name,
        String tags,
        Long categoryId) {}
