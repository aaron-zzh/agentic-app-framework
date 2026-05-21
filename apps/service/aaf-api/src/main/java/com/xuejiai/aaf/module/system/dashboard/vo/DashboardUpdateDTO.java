package com.xuejiai.aaf.module.system.dashboard.vo;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

/** 更新仪表盘请求。 */
@Schema(description = "更新仪表盘")
public record DashboardUpdateDTO(
        @Size(max = 100) String name,
        @Size(max = 500) String description,
        Boolean isDefault,
        @Valid List<WidgetCreateDTO> widgets) {}
