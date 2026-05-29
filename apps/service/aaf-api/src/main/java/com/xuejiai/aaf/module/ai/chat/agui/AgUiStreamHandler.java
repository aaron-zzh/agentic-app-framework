package com.xuejiai.aaf.module.ai.chat.agui;

import java.io.IOException;
import java.util.UUID;

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

/**
 * 将 Spring AI 的 Flux&lt;ChatResponse&gt; 转换为 AG-UI 协议事件流
 *
 * @deprecated 已由 agentscope-agui-spring-boot-starter 替代。
 *     新代码请使用 AgentScope AG-UI 端点 /agui/runs。保留此类用于兼容旧接口。
 * @author AaronZZH & Kiro
 */
@Deprecated(since = "0.1.0", forRemoval = false)
@Slf4j
@Component
@RequiredArgsConstructor
public class AgUiStreamHandler {

    private final ObjectMapper objectMapper;

    /**
     * 订阅 LLM 流式响应，按 AG-UI 协议向 SseEmitter 发送事件
     *
     * @param flux LLM 流式响应
     * @param emitter SSE 发射器
     * @param runId 本次运行 ID
     */
    public void handleStream(Flux<ChatResponse> flux, SseEmitter emitter, String runId) {
        handleStream(flux, emitter, runId, null);
    }

    /**
     * 订阅 LLM 流式响应，流结束后回调完整内容（用于持久化）
     *
     * @param flux LLM 流式响应
     * @param emitter SSE 发射器
     * @param runId 本次运行 ID
     * @param onComplete 流结束回调，参数为完整 AI 回复文本（可为 null 表示不需要回调）
     */
    public void handleStream(
            Flux<ChatResponse> flux,
            SseEmitter emitter,
            String runId,
            java.util.function.Consumer<String> onComplete) {
        var messageId = UUID.randomUUID().toString();
        var fullContent = new StringBuilder();

        // 发送 RUN_STARTED + TEXT_MESSAGE_START
        sendEvent(emitter, AgUiEvent.runStarted(runId));
        sendEvent(emitter, AgUiEvent.textMessageStart(runId, messageId));

        flux.doOnNext(
                        response -> {
                            if (response.getResult() == null
                                    || response.getResult().getOutput() == null) {
                                return;
                            }
                            var output = response.getResult().getOutput();

                            // 处理文本内容
                            var text = output.getText();
                            if (text != null && !text.isEmpty()) {
                                fullContent.append(text);
                                sendEvent(
                                        emitter,
                                        AgUiEvent.textMessageContent(runId, messageId, text));
                            }

                            // 处理工具调用
                            var toolCalls = output.getToolCalls();
                            if (toolCalls != null && !toolCalls.isEmpty()) {
                                for (var toolCall : toolCalls) {
                                    var toolCallId =
                                            toolCall.id() != null
                                                    ? toolCall.id()
                                                    : UUID.randomUUID().toString();
                                    sendEvent(
                                            emitter,
                                            AgUiEvent.toolCallStart(
                                                    runId, toolCallId, toolCall.name()));
                                    if (toolCall.arguments() != null) {
                                        sendEvent(
                                                emitter,
                                                AgUiEvent.toolCallArgs(
                                                        runId, toolCallId, toolCall.arguments()));
                                    }
                                    sendEvent(emitter, AgUiEvent.toolCallEnd(runId, toolCallId));
                                }
                            }
                        })
                .doOnComplete(
                        () -> {
                            sendEvent(emitter, AgUiEvent.textMessageEnd(runId, messageId));
                            sendEvent(emitter, AgUiEvent.runFinished(runId));
                            // 触发完整内容回调（用于持久化）
                            if (onComplete != null) {
                                onComplete.accept(fullContent.toString());
                            }
                            emitter.complete();
                        })
                .doOnError(
                        e -> {
                            log.error("AG-UI 流式调用异常: runId={}", runId, e);
                            sendEvent(emitter, AgUiEvent.runError(runId, e.getMessage()));
                            completeWithError(emitter, e);
                        })
                .subscribe();
    }

    private void sendEvent(SseEmitter emitter, AgUiEvent event) {
        try {
            var json = objectMapper.writeValueAsString(event);
            emitter.send(SseEmitter.event().data(json));
        } catch (IOException e) {
            log.debug("AG-UI SSE 发送失败（客户端可能已断开）: {}", e.getMessage());
        }
    }

    private void completeWithError(SseEmitter emitter, Throwable e) {
        try {
            emitter.completeWithError(e);
        } catch (Exception ignored) {
            // 客户端已断开
        }
    }
}
