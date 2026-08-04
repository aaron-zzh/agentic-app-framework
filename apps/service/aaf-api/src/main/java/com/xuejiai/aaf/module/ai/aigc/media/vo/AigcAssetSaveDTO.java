package com.xuejiai.aaf.module.ai.aigc.media.vo;

/** 将媒体登记为资产时的可选元数据。 */
public record AigcAssetSaveDTO(Long categoryId, String scope, String copyrightInfo) {}
