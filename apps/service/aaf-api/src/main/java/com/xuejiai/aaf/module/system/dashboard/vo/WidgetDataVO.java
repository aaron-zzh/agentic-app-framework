package com.xuejiai.aaf.module.system.dashboard.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 组件数据查询结果。
 *
 * <p>widgetId 为字符串标识——可能是数据库 ID 或预设里的语义化 ID（如 "admin-kb-count"）， 后端不解析其语义，仅原样回传供前端关联。
 *
 * @author AaronZZH & Kiro
 */
@Schema(description = "组件数据")
public record WidgetDataVO(String widgetId, String type, Object data) {}
