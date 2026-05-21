package com.xuejiai.aaf.module.system.dashboard.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/** 仪表盘组件响应。 */
@Schema(description = "仪表盘组件信息")
public record DashboardWidgetVO(
        Long id, String type, String title, String position, String config, Integer sortOrder) {}
