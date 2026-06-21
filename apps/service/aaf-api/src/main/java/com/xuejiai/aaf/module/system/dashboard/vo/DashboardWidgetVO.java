package com.xuejiai.aaf.module.system.dashboard.vo;

import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 仪表盘组件响应。
 *
 * <p>{@code id} 统一为字符串：来自 DB 的 widget 由 Long 转 String，预设里的 widget 直接是 string 字面量（如
 * "ops-dau-trend"），前端类型也是 string，避免 number/string 比较错位。
 *
 * <p>{@code position} 与 {@code config} 是已解析的结构化对象——存储层在 entity 里仍用 String 承载 JSON 文本，VO/DTO 与前端契约层
 * 一律走对象。{@code config} 因 Widget 类型多态，使用 {@code Map} 承载，由前端按 {@code config.type} 区分。
 *
 * @author AaronZZH &amp; Kiro
 */
@Schema(description = "仪表盘组件信息")
public record DashboardWidgetVO(
        String id,
        String type,
        String title,
        WidgetPositionVO position,
        Map<String, Object> config,
        Integer sortOrder) {}
