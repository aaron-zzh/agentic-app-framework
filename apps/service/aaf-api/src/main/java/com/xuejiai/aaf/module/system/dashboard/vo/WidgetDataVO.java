package com.xuejiai.aaf.module.system.dashboard.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/** 组件数据查询结果。 */
@Schema(description = "组件数据")
public record WidgetDataVO(Long widgetId, String type, Object data) {}
