package com.xuejiai.aaf.module.system.dashboard.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Widget 位置（react-grid-layout 格式）。
 *
 * @author AaronZZH &amp; Kiro
 */
@Schema(description = "Widget 位置")
public record WidgetPositionVO(int x, int y, int w, int h) {}
