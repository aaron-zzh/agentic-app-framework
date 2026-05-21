package com.xuejiai.aaf.module.system.config.vo;

import java.time.LocalDateTime;

/** 系统配置响应。敏感配置（visible=false）的 value 返回 null。 */
public record SystemConfigVO(
        Long id,
        String category,
        String configKey,
        String value,
        String defaultValue,
        String valueType,
        String name,
        String description,
        Boolean visible,
        Boolean editable,
        LocalDateTime updateTime) {}
