package com.xuejiai.aaf.module.system.config.vo;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 系统配置响应。敏感配置（visible=false）的 value 返回 null。
 *
 * @author AaronZZH & Kiro
 */
public record SystemConfigVO(
        @Schema(description = "主键 ID") Long id,
        @Schema(description = "分类") String category,
        @Schema(description = "配置键") String configKey,
        @Schema(description = "值") String value,
        @Schema(description = "默认值") String defaultValue,
        String valueType,
        @Schema(description = "名称") String name,
        @Schema(description = "描述") String description,
        @Schema(description = "是否可见") Boolean visible,
        Boolean editable,
        @Schema(description = "更新时间") LocalDateTime updateTime) {}
