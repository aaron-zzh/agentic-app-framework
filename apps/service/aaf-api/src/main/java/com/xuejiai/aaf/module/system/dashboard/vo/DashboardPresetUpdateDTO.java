package com.xuejiai.aaf.module.system.dashboard.vo;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

/**
 * 更新仪表盘预设请求（字段可选，null 表示不修改）。
 *
 * @author AaronZZH &amp; Kiro
 */
@Schema(description = "更新仪表盘预设")
public record DashboardPresetUpdateDTO(
        @Size(max = 100) String name,
        @Size(max = 500) String description,
        Boolean adminOnly,
        Integer refreshInterval,
        @Valid List<WidgetCreateDTO> widgets) {}
