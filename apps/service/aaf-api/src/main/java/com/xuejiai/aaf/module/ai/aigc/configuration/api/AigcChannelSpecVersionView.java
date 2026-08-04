package com.xuejiai.aaf.module.ai.aigc.configuration.api;

/** 已发布渠道规格版本视图。 */
public record AigcChannelSpecVersionView(
        Long id,
        String code,
        String name,
        String specVersion,
        String exportFormat,
        Integer maxDurationSeconds) {}
