package com.xuejiai.aaf.module.ai.chat.listener;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.intelligent.agent.trace.UserMessageEvent;
import com.xuejiai.aaf.module.ai.chat.service.ChatService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 用户消息持久化监听器——异步写入聊天记录。
 *
 * <p>conversationId 仅在可解析为有效 sessionId 时写入。
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
}
