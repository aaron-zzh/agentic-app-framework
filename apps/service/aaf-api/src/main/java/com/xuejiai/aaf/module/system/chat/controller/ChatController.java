package com.xuejiai.aaf.module.system.chat.controller;

import java.io.IOException;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.intelligent.ai.chat.AiProperties;
import com.xuejiai.aaf.framework.intelligent.ai.chat.ChatContextBuilder;
import com.xuejiai.aaf.framework.intelligent.ai.chat.ChatContextBuilder.HistoryMessage;
import com.xuejiai.aaf.framework.intelligent.ai.chat.ResilientChatService;
import com.xuejiai.aaf.framework.security.ActorContext;
import com.xuejiai.aaf.module.system.chat.service.ChatService;
import com.xuejiai.aaf.module.system.chat.service.IntentService;
import com.xuejiai.aaf.module.system.chat.vo.ChatMessageSendDTO;
import com.xuejiai.aaf.module.system.chat.vo.ChatMessageVO;
import com.xuejiai.aaf.module.system.chat.vo.ChatSessionCreateDTO;
import com.xuejiai.aaf.module.system.chat.vo.ChatSessionVO;
import com.xuejiai.aaf.module.system.chat.vo.ChatStreamDTO;
import com.xuejiai.aaf.module.system.chat.vo.IntentClassifyDTO;
import com.xuejiai.aaf.module.system.chat.vo.IntentResult;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 聊天接口。
 *
 * @author AaronZZH & Kiro
 */
@Slf4j
@Tag(name = "聊天")
@RestController
@RequestMapping("/api/system/chat")
@RequiredArgsConstructor
public class ChatController {

    private static final long SSE_TIMEOUT = 5 * 60 * 1000L;

    private final ChatService chatService;
    private final IntentService intentService;
    private final ActorContext actorContext;
    private final ResilientChatService resilientChatService;
    private final ChatContextBuilder chatContextBuilder;
    private final AiProperties aiProperties;

    @Operation(summary = "意图识别")
    @PostMapping("/intent")
    public Result<IntentResult> classifyIntent(@RequestBody @Validated IntentClassifyDTO dto) {
        return Result.success(intentService.classify(dto.text()));
    }

    @Operation(summary = "创建会话")
    @PostMapping("/sessions")
    public Result<ChatSessionVO> createSession(@RequestBody @Validated ChatSessionCreateDTO dto) {
        var userId = actorContext.currentUserId().orElseThrow();
        return Result.success(chatService.createSession(userId, dto));
    }

    @Operation(summary = "获取会话列表")
    @GetMapping("/sessions")
    public Result<List<ChatSessionVO>> listSessions() {
        var userId = actorContext.currentUserId().orElseThrow();
        return Result.success(chatService.listSessions(userId));
    }

    @Operation(summary = "获取会话消息历史")
    @GetMapping("/sessions/{sessionId}/messages")
    public Result<List<ChatMessageVO>> listMessages(@PathVariable Long sessionId) {
        return Result.success(chatService.listMessages(sessionId));
    }

    @Operation(summary = "分页获取会话消息")
    @GetMapping("/sessions/{sessionId}/messages/page")
    public Result<Page<ChatMessageVO>> getMessagesPaged(
            @PathVariable Long sessionId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(chatService.getMessagesPaged(sessionId, page, size));
    }

    @Operation(summary = "归档会话")
    @PostMapping("/sessions/{sessionId}/archive")
    public Result<Void> archiveSession(@PathVariable Long sessionId) {
        chatService.archiveSession(sessionId);
        return Result.success(null);
    }

    @Operation(summary = "发送消息")
    @PostMapping("/messages")
    public Result<ChatMessageVO> sendMessage(@RequestBody @Validated ChatMessageSendDTO dto) {
        var userId = actorContext.currentUserId().orElseThrow();
        var message =
                chatService.saveMessage(userId, "HUMAN", dto.sessionId(), "user", dto.content());
        return Result.success(message);
    }

    @Operation(summary = "AI 流式对话")
    @PostMapping("/sessions/{sessionId}/stream")
    public SseEmitter streamChat(
            @PathVariable Long sessionId, @RequestBody @Validated ChatStreamDTO dto) {

        var userId = actorContext.currentUserId().orElseThrow();
        var emitter = new SseEmitter(SSE_TIMEOUT);

        // 注册清理回调
        emitter.onCompletion(() -> log.debug("SSE 完成: sessionId={}", sessionId));
        emitter.onTimeout(() -> log.warn("SSE 超时: sessionId={}", sessionId));

        // 保存用户消息
        chatService.saveMessage(userId, "HUMAN", sessionId, "user", dto.content());

        // 构建上下文
        var history =
                chatService.listMessages(sessionId).stream()
                        .map(m -> new HistoryMessage(m.role(), m.content()))
                        .toList();
        var systemPrompt = aiProperties.getPrompts().getOrDefault("chat", "你是一个有帮助的 AI 助手。");
        var messages = chatContextBuilder.buildMessages(systemPrompt, history, dto.content(), 4096);

        // 虚拟线程中执行流式调用
        Thread.startVirtualThread(
                () -> {
                    var fullContent = new StringBuilder();
                    try {
                        var flux = resilientChatService.stream(messages, "chat", userId);
                        flux.doOnNext(
                                        response -> {
                                            if (response.getResult() != null
                                                    && response.getResult().getOutput() != null) {
                                                var token =
                                                        response.getResult().getOutput().getText();
                                                if (token != null && !token.isEmpty()) {
                                                    fullContent.append(token);
                                                    sendEvent(
                                                            emitter,
                                                            "{\"token\":\"%s\",\"done\":false}"
                                                                    .formatted(escapeJson(token)));
                                                }
                                            }
                                        })
                                .doOnComplete(
                                        () -> {
                                            // 保存 AI 回复
                                            chatService.saveMessage(
                                                    0L,
                                                    "AI",
                                                    sessionId,
                                                    "assistant",
                                                    fullContent.toString());
                                            sendEvent(
                                                    emitter,
                                                    "{\"token\":\"\",\"done\":true,\"usage\":{\"promptTokens\":0,\"completionTokens\":0}}");
                                            emitter.complete();
                                        })
                                .doOnError(
                                        e -> {
                                            log.error("流式调用异常: sessionId={}", sessionId, e);
                                            completeWithError(emitter, e);
                                        })
                                .subscribe();
                    } catch (Exception e) {
                        log.error("启动流式调用失败: sessionId={}", sessionId, e);
                        completeWithError(emitter, e);
                    }
                });

        return emitter;
    }

    private void sendEvent(SseEmitter emitter, String data) {
        try {
            emitter.send(SseEmitter.event().data(data));
        } catch (IOException e) {
            log.debug("SSE 发送失败（客户端可能已断开）: {}", e.getMessage());
        }
    }

    private void completeWithError(SseEmitter emitter, Throwable e) {
        try {
            emitter.completeWithError(e);
        } catch (Exception ignored) {
            // 客户端已断开
        }
    }

    private String escapeJson(String text) {
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
