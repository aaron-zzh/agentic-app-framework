/*
 * Copyright 2024-2026 xuejiai.com & AaronZZH.
 * Licensed under the Apache License, Version 2.0.
 */
package com.xuejiai.aaf.framework.agentscope.middleware;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.agentscope.tool.SendUiTool;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.CustomEvent;
import io.agentscope.core.event.ToolResultEndEvent;
import io.agentscope.core.event.ToolResultTextDeltaEvent;
import io.agentscope.core.middleware.ActingInput;
import io.agentscope.core.middleware.MiddlewareBase;
import reactor.core.publisher.Flux;

/**
 * UI 事件中间件——拦截 {@link SendUiTool} 的工具返回值（含 {@code __ui__:true} 标记）， 发出 agentscope {@link
 * CustomEvent}（name={@code "ui_block"}）到前端 SSE 流。
 *
 * <p>前端在 AG-UI 事件流里监听 {@code type="CUSTOM"} + {@code name="ui_block"} 事件来渲染 UI 块。
 *
 * <p>工作机制：
 *
 * <ol>
 *   <li>{@code onActing} 拼接各工具的 {@link ToolResultTextDeltaEvent} delta 片段
 *   <li>检测到 {@link ToolResultEndEvent} 时，若拼好的结果含 {@code __ui__:true} 标记，则额外发出 {@link CustomEvent}
 *   <li>原始工具结果事件不删除，确保 LLM 仍能看到工具返回内容
 * </ol>
 */
public class UiEventMiddleware implements MiddlewareBase {

    private static final Logger log = LoggerFactory.getLogger(UiEventMiddleware.class);

    @Override
    public Flux<AgentEvent> onActing(
            Agent agent,
            RuntimeContext ctx,
            ActingInput input,
            Function<ActingInput, Flux<AgentEvent>> next) {

        // toolCallId → 已拼接的 delta 内容
        Map<String, StringBuilder> buffers = new ConcurrentHashMap<>();

        return next.apply(input)
                .flatMap(
                        event -> {
                            if (event instanceof ToolResultTextDeltaEvent e) {
                                buffers.computeIfAbsent(e.getToolCallId(), k -> new StringBuilder())
                                        .append(e.getDelta() == null ? "" : e.getDelta());
                                return Flux.just(event);
                            }

                            if (event instanceof ToolResultEndEvent e) {
                                var buf = buffers.remove(e.getToolCallId());
                                if (buf == null) return Flux.just(event);

                                String result = buf.toString();
                                // 判断是否含 UI 标记
                                if (!result.contains("\"" + SendUiTool.UI_MARKER + "\"")) {
                                    return Flux.just(event);
                                }

                                // 解析并发出 CustomEvent
                                try {
                                    @SuppressWarnings("unchecked")
                                    Map<String, Object> uiData = JsonUtils.parseObject(result, Map.class);
                                    if (!Boolean.TRUE.equals(uiData.get(SendUiTool.UI_MARKER))) {
                                        return Flux.just(event);
                                    }
                                    // 构建 CustomEvent payload
                                    Map<String, Object> payload = new HashMap<>(uiData);
                                    payload.remove(SendUiTool.UI_MARKER);
                                    var customEvent = new CustomEvent("ui_block", payload);
                                    log.debug(
                                            "[UiEvent] ui_block emitted toolCallId={} uiType={}",
                                            e.getToolCallId(),
                                            uiData.get("uiType"));
                                    // 先发原始结果事件，再发 CustomEvent
                                    return Flux.just(event, customEvent);
                                } catch (Exception ex) {
                                    log.warn("[UiEvent] 解析 UI 标记失败: {}", ex.getMessage());
                                    return Flux.just(event);
                                }
                            }

                            return Flux.just(event);
                        });
    }
}
