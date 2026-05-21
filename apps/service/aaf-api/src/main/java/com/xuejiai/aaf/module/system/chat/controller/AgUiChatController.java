package com.xuejiai.aaf.module.system.chat.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.intelligent.ai.AiProperties;
import com.xuejiai.aaf.framework.intelligent.ai.ResilientChatService;
import com.xuejiai.aaf.framework.security.ActorContext;
import com.xuejiai.aaf.module.system.chat.agui.AgUiEvent;
import com.xuejiai.aaf.module.system.chat.agui.AgUiStreamHandler;
import com.xuejiai.aaf.module.system.chat.agui.ToolRegistry;
import com.xuejiai.aaf.module.system.chat.agui.ToolRegistry.ToolDefinition;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * AG-UI 协议端点。
 *
 * <p>实现 assistant-ui 前端组件库的标准 SSE 事件流协议。
 *
 * @author AaronZZH & Kiro
 */
@Slf4j
@Tag(name = "AG-UI 协议")
@RestController
@RequestMapping("/api/chat/agent")
@RequiredArgsConstructor
public class AgUiChatController {

    private static final long SSE_TIMEOUT = 5 * 60 * 1000L;

    private final ResilientChatService resilientChatService;
    private final AgUiStreamHandler streamHandler;
    private final ToolRegistry toolRegistry;
    private final ActorContext actorContext;
    private final AiProperties aiProperties;

    // ========== 请求/响应 DTO ==========

    /** AG-UI 运行请求 */
    public record AgUiRunRequest(
            @NotBlank String threadId,
            @NotNull List<AgUiMessage> messages,
            Object state,
            List<AgUiTool> tools) {}

    /** AG-UI 消息 */
    public record AgUiMessage(String role, String content) {}

    /** AG-UI 工具定义（前端传入） */
    public record AgUiTool(String name, String description, String parameters) {}

    /** 工具调用结果提交 */
    public record ToolResultRequest(
            @NotBlank String runId, @NotBlank String toolCallId, @NotBlank String result) {}

    // ========== 端点 ==========

    @Operation(summary = "AG-UI 协议主端点 — 启动 Agent 运行")
    @PostMapping("/run")
    public SseEmitter run(@RequestBody @Valid AgUiRunRequest request) {
        var userId = actorContext.currentUserId().orElse(0L);
        var emitter = new SseEmitter(SSE_TIMEOUT);
        var runId = UUID.randomUUID().toString();

        emitter.onCompletion(() -> log.debug("AG-UI SSE 完成: runId={}", runId));
        emitter.onTimeout(() -> log.warn("AG-UI SSE 超时: runId={}", runId));

        // 构建 Spring AI 消息列表
        var messages = buildMessages(request);

        // 虚拟线程中执行流式调用
        Thread.startVirtualThread(
                () -> {
                    try {
                        var flux = resilientChatService.stream(messages, "chat", userId);
                        streamHandler.handleStream(flux, emitter, runId);
                    } catch (Exception e) {
                        log.error("AG-UI 启动流式调用失败: runId={}", runId, e);
                        try {
                            var json =
                                    new com.fasterxml.jackson.databind.ObjectMapper()
                                            .writeValueAsString(
                                                    AgUiEvent.runError(runId, e.getMessage()));
                            emitter.send(SseEmitter.event().data(json));
                        } catch (Exception ignored) {
                            // 忽略
                        }
                        try {
                            emitter.completeWithError(e);
                        } catch (Exception ignored) {
                            // 客户端已断开
                        }
                    }
                });

        return emitter;
    }

    @Operation(summary = "提交工具调用结果")
    @PostMapping("/tool-result")
    public Result<Void> submitToolResult(@RequestBody @Valid ToolResultRequest request) {
        log.info("收到工具调用结果: runId={}, toolCallId={}", request.runId(), request.toolCallId());
        // TODO: 将工具结果注入到对应的运行上下文中，触发后续 LLM 调用
        return Result.success(null);
    }

    @Operation(summary = "获取可用工具列表")
    @GetMapping("/tools")
    public Result<List<ToolDefinition>> listTools() {
        return Result.success(List.copyOf(toolRegistry.getAll()));
    }

    // ========== 内部方法 ==========

    private List<Message> buildMessages(AgUiRunRequest request) {
        var systemPrompt = aiProperties.getPrompts().getOrDefault("chat", "你是一个有帮助的 AI 助手。");
        var messages = new java.util.ArrayList<Message>();
        messages.add(new SystemMessage(systemPrompt));

        for (var msg : request.messages()) {
            messages.add(
                    switch (msg.role()) {
                        case "assistant" -> new AssistantMessage(msg.content());
                        case "system" -> new SystemMessage(msg.content());
                        default -> new UserMessage(msg.content());
                    });
        }
        return messages;
    }
}
