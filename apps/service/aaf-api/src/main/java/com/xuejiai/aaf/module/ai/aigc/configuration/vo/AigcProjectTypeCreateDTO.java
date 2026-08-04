package com.xuejiai.aaf.module.ai.aigc.configuration.vo;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 项目类型创建请求。
 *
 * @author AaronZZH & Kiro
 */
@Schema(description = "创建项目类型")
public record AigcProjectTypeCreateDTO(
        @NotBlank String code,
        @NotBlank String name,
        String icon,
        String description,
        String briefPlaceholder,
        @NotBlank String definitionVersion,
        List<String> defaultChannels,
        String defaultProductionMode,
        @NotNull Boolean quickEntry,
        @NotNull Integer sortOrder) {}
