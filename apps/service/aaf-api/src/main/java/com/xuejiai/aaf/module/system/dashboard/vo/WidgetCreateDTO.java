package com.xuejiai.aaf.module.system.dashboard.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 组件创建请求。
 *
 * @author AaronZZH & Kiro
 */
@Schema(description = "创建仪表盘组件")
public record WidgetCreateDTO(
        @NotBlank @Size(max = 20) String type,
        @NotBlank @Size(max = 100) String title,
        @NotNull String position,
        @NotNull String config,
        Integer sortOrder) {}
