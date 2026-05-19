package com.xuejiai.aaf.module.system.vo;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/** 创建仪表盘请求。 */
@Schema(description = "创建仪表盘")
public record DashboardCreateDTO(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 500) String description,
        Boolean isDefault,
        @Valid List<WidgetCreateDTO> widgets) {}
