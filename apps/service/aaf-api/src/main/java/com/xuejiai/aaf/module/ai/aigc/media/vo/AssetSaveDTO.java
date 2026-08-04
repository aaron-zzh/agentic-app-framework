package com.xuejiai.aaf.module.ai.aigc.media.vo;

/** 将 Media 标记为 Asset 时的可选元数据。 */
public record AssetSaveDTO(Long categoryId, String scope, String copyrightInfo) {}
