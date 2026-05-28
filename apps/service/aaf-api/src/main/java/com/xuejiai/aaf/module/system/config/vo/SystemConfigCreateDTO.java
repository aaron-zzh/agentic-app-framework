package com.xuejiai.aaf.module.system.config.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 创建系统配置请求。
 *
 * @author AaronZZH & Kiro
 */
public record SystemConfigCreateDTO(
        @Schema(description = "分类") @NotBlank String category,
        @Schema(description = "配置键") @NotBlank String configKey,
        @Schema(description = "配置值") String value,
        @Schema(description = "默认值") String defaultValue,
        @Schema(description = "值类型：string/integer/boolean/json") String valueType,
        @Schema(description = "名称") @NotBlank String name,
        @Schema(description = "描述") String description,
        @Schema(description = "是否前端可见") Boolean visible,
        @Schema(description = "是否可编辑") Boolean editable) {}
