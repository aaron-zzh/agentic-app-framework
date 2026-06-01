package com.xuejiai.aaf.module.ai.chat.agui;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xuejiai.aaf.framework.intelligent.agent.run.AgentRunEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 将统一 AgentRunEvent 桥接到当前运行的 SSE 通道。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentRunEventStreamService {

    /** SSE 帧格式：AG-UI 协议 CUSTOM 事件（@ag-ui/client 消费）或自定义命名事件。 */
    public enum Format {
        AGUI_CUSTOM,
        NAMED
    }

    private final ObjectMapper objectMapper;
    private final Map<String, List<Target>> emitters = new ConcurrentHashMap<>();

    private record Target(SseEmitter emitter, Format format) {}

    public void attach(String runId, SseEmitter emitter, Format format) {
        var target = new Target(emitter, format);
        emitters.computeIfAbsent(runId, ignored -> new CopyOnWriteArrayList<>()).add(target);
        emitter.onCompletion(() -> detach(runId, target));
        emitter.onTimeout(() -> detach(runId, target));
        emitter.onError(error -> detach(runId, target));
    }

    @EventListener
    public void onAgentRunEvent(AgentRunEvent event) {
        if (event.runId() == null || event.runId().isBlank()) {
            return;
        }
        var targets = emitters.get(event.runId());
        if (targets == null || targets.isEmpty()) {
            return;
        }
        for (var target : targets) {
            send(event.runId(), target, event);
        }
    }

    private void send(String runId, Target target, AgentRunEvent event) {
        try {
            // AG-UI 流：包成协议内 CUSTOM 事件（走默认 data 通道，@ag-ui/client 的 onCustomEvent 接）；
            // 非 AG-UI 流：仍走命名事件 agent-run，不污染其自有协议。
            Object payload =
                    target.format() == Format.AGUI_CUSTOM
                            ? Map.of("type", "CUSTOM", "name", "agent-run", "value", event)
                            : event;
            var json = objectMapper.writeValueAsString(payload);
            synchronized (target.emitter()) {
                var frame =
                        target.format() == Format.AGUI_CUSTOM
                                ? SseEmitter.event().data(json)
                                : SseEmitter.event().name("agent-run").data(json);
                target.emitter().send(frame);
            }
        } catch (IOException ex) {
            log.debug("AgentRunEvent SSE 发送失败: runId={}, error={}", runId, ex.getMessage());
            detach(runId, target);
        }
    }

    private void detach(String runId, Target target) {
        var targets = emitters.get(runId);
        if (targets == null) {
            return;
        }
        targets.remove(target);
        if (targets.isEmpty()) {
            emitters.remove(runId);
        }
    }
}
