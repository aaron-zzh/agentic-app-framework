package com.xuejiai.aaf.module.ai.chat.listener;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.intelligent.agent.trace.ExecutionCompletedEvent;
import com.xuejiai.aaf.framework.intelligent.agent.trace.ExecutionStatus;
import com.xuejiai.aaf.framework.intelligent.agent.trace.UserMessageEvent;
import com.xuejiai.aaf.module.ai.chat.service.ChatService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 聊天记录持久化监听器——AgentScope 链路执行完成后异步写入 DB。
 *
 * <p>conversationId 对应 sessionId（由 AgentRunContextHolder.runId 传入）， 仅当 userId 和 conversationId
 * 均非空且为有效数字时写入。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatPersistenceListener {

    private final ChatService chatService;

    @Async
    @EventListener
    public void onUserMessage(UserMessageEvent event) {
        Long sessionId;
        try {
            sessionId = Long.valueOf(event.conversationId());
        } catch (NumberFormatException e) {
            return;
        }
        try {
            chatService.saveMessage(event.userId(), "HUMAN", sessionId, "user", event.content());
        } catch (Exception e) {
            log.warn("用户消息持久化失败 [session={}]: {}", sessionId, e.getMessage());
        }
    }

    @Async
    @EventListener
    public void onExecutionCompleted(ExecutionCompletedEvent event) {
        if (event.userId() == null || event.conversationId() == null) {
            return;
        }
        if (event.status() != ExecutionStatus.SUCCESS) {
            return;
        }
        Long sessionId;
        try {
            sessionId = Long.valueOf(event.conversationId());
        } catch (NumberFormatException e) {
            // conversationId 是 runId（UUID），不是 sessionId，跳过
            return;
        }
        try {
            // 写入 AI 回复（用户消息已由入口层写入）
            if (event.output() != null && !event.output().isBlank()) {
                chatService.saveMessage(0L, "AI", sessionId, "assistant", event.output());
            }
        } catch (Exception e) {
            log.warn("聊天记录持久化失败 [session={}]: {}", sessionId, e.getMessage());
        }
    }
}
