package com.xuejiai.aaf.module.system.dashboard.vo;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 仪表盘预设响应。
 *
 * <p>{@code widgets} 已从 entity 里的 JSON 文本解析为结构化列表，前端可直接迭代。
 *
 * @author AaronZZH &amp; Kiro
 */
@Schema(description = "仪表盘预设")
public record DashboardPresetVO(
        String id,
        String presetKey,
        String name,
        String description,
        boolean adminOnly,
        int refreshInterval,
        List<DashboardWidgetVO> widgets,
        int sortOrder) {}
