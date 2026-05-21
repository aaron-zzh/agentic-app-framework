package com.xuejiai.aaf.module.system.dashboard.vo;

import java.time.LocalDateTime;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/** 仪表盘响应。 */
@Schema(description = "仪表盘信息")
public record DashboardVO(
        Long id,
        String name,
        String description,
        Boolean isDefault,
        List<DashboardWidgetVO> widgets,
        LocalDateTime createTime,
        LocalDateTime updateTime) {}
