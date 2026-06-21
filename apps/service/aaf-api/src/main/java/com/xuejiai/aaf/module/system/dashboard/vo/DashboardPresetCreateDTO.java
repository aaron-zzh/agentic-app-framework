package com.xuejiai.aaf.module.system.dashboard.vo;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 创建仪表盘预设请求。
 *
 * @author AaronZZH &amp; Kiro
 */
@Schema(description = "创建仪表盘预设")
public record DashboardPresetCreateDTO(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 500) String description,
        Boolean adminOnly,
        Integer refreshInterval,
        /** Widget 布局，结构化列表；服务端落盘时序列化为 jsonb */
        @Valid List<WidgetCreateDTO> widgets) {}
