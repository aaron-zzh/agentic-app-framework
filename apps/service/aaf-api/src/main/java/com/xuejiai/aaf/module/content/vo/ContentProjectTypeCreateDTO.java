package com.xuejiai.aaf.module.content.vo;

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
public record ContentProjectTypeCreateDTO(
        @NotBlank String code,
        @NotBlank String name,
        String icon,
        String description,
        String briefPlaceholder,
        List<String> defaultChannels,
        String defaultProductionMode,
        @NotNull Boolean quickEntry,
        @NotNull Boolean builtin,
        @NotNull Integer sortOrder,
        @NotBlank String status) {}
