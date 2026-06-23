/*
 * Copyright 2024-2026 xuejiai.com & AaronZZH.
 * Licensed under the Apache License, Version 2.0.
 */
package com.xuejiai.aaf.framework.agentscope.tool;

import com.xuejiai.aaf.common.util.JsonUtils;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;

/**
 * 向前端推送结构化 UI 块的工具。
 *
 * <p>工具返回带 {@code __ui__:true} 标记的 JSON，由 {@link
 * com.xuejiai.aaf.framework.agentscope.middleware.UiEventMiddleware} 在 {@code onActing} 里检测到后发出
 * agentscope {@link io.agentscope.core.event.CustomEvent}（name={@code "ui_block"}）， 前端在 AG-UI
 * 事件流里监听 {@code "ui_block"} 事件渲染 UI。
 *
 * <p>支持的 UI 类型（{@code uiType}）：
 *
 * <ul>
 *   <li>{@code card} — 信息卡片（标题 + 内容 + 可选按钮）
 *   <li>{@code form} — 表单（字段列表 + 提交按钮）
 *   <li>{@code chart} — 图表（ECharts 配置 JSON）
 *   <li>{@code table} — 表格（columns + rows）
 *   <li>{@code markdown} — Markdown 富文本块
 * </ul>
 */
public class SendUiTool {

    /** 工具结果中的标记键，供 UiEventMiddleware 识别 */
    public static final String UI_MARKER = "__ui__";

    @Tool(
            description =
                    "向用户界面推送一个结构化 UI 块（卡片/表单/图表/表格/Markdown）。"
                            + "当内容超过纯文本所能表达的结构时使用，如展示数据报表、采集表单输入、渲染图表。"
                            + "返回成功标识后等待用户在界面完成交互。")
    public String send_ui(
            @ToolParam(
                            name = "uiType",
                            description = "UI 类型：card | form | chart | table | markdown")
                    String uiType,
            @ToolParam(name = "title", description = "UI 块标题（可选）") String title,
            @ToolParam(
                            name = "payload",
                            description =
                                    "结构化内容 JSON 字符串。"
                                            + "card: {\"content\":\"...\",\"actions\":[{\"label\":\"确认\",\"value\":\"ok\"}]}；"
                                            + "form: {\"fields\":[{\"name\":\"email\",\"label\":\"邮箱\",\"type\":\"text\"}]}；"
                                            + "chart: ECharts option JSON；"
                                            + "table: {\"columns\":[\"列1\",\"列2\"],\"rows\":[[\"a\",\"b\"]]}；"
                                            + "markdown: {\"content\":\"## 标题\\n...\"}；")
                    String payload) {
        try {
            var node = JsonUtils.createObjectNode();
            node.put(UI_MARKER, true);
            node.put("uiType", uiType);
            if (title != null && !title.isBlank()) node.put("title", title);
            // payload 作为内嵌 JSON 节点（若无法解析则作为字符串）
            try {
                node.set("payload", JsonUtils.readTree(payload));
            } catch (Exception e) {
                node.put("payload", payload);
            }
            return JsonUtils.toJsonString(node);
        } catch (Exception e) {
            return "{\"status\":\"error\",\"message\":\"UI 块序列化失败: " + e.getMessage() + "\"}";
        }
    }
}
