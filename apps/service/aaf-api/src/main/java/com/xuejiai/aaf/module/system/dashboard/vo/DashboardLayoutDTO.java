package com.xuejiai.aaf.module.system.dashboard.vo;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * 保存仪表盘布局请求。
 *
 * <p>对应前端 {@code useSaveDashboardLayout}：{@code PUT /system/dashboards/{id}/layout}，请求体形如 {@code {
 * layout: [...] }}。
 *
 * @author AaronZZH &amp; Kiro
 */
@Schema(description = "保存仪表盘布局")
public record DashboardLayoutDTO(@NotNull @Valid List<WidgetCreateDTO> layout) {}
