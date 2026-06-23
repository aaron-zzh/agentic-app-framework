/*
 * Copyright 2024-2026 xuejiai.com & AaronZZH.
 * Licensed under the Apache License, Version 2.0.
 */
package com.xuejiai.aaf.framework.agentscope.middleware;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import com.xuejiai.aaf.framework.agentscope.runtime.AafContextHolder;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentResultEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.middleware.AgentInput;
import io.agentscope.core.middleware.MiddlewareBase;
import reactor.core.publisher.Flux;

/**
 * 把 Agent 的每次调用桥接到 AAF 的 {@code conversation_message} 表。
 *
 * <p>挂钩 {@link MiddlewareBase#onAgent}，捕获两个时机：
 *
 * <ol>
 *   <li>调用入口：把 {@code input.msgs()} 中的每条用户消息插入 conversation_message（role=user, sender_type=HUMAN）
 *   <li>调用结束：从事件流中拦截 {@link AgentResultEvent} 拿到最终的 assistant Msg，插入 conversation_message
 *       （role=assistant, sender_type=ASSISTANT）
 * </ol>
 *
 * <p>所属 conversation 由 {@link AafContextHolder#conversationId()} 提供——若为 null（如外部用户首次以新 threadId
 * 进入）， 则跳过持久化（避免 FK 异常）。
 *
 * <p>注：目前不写 {@code ai_llm_call_log} / {@code ai_tool_call_log}（那两张表的 token / 工具调用粒度更细， 需要 hook
 * {@code onModelCall} / {@code onActing}）。Phase-3 P1 再补。
 */
public class ConversationBridgeMiddleware implements MiddlewareBase {

    private static final Logger log = LoggerFactory.getLogger(ConversationBridgeMiddleware.class);

    private final JdbcTemplate jdbc;

    public ConversationBridgeMiddleware(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Flux<AgentEvent> onAgent(
            Agent agent,
            RuntimeContext ctx,
            AgentInput input,
            Function<AgentInput, Flux<AgentEvent>> next) {

        Long conversationId = AafContextHolder.conversationId();
        Long userId = AafContextHolder.userId();
        Long assistantId = AafContextHolder.assistantId();

        // 入口：写所有 user 消息
        if (conversationId != null && input.msgs() != null) {
            for (Msg msg : input.msgs()) {
                if (msg == null) continue;
                writeUserMessage(conversationId, userId, msg);
            }
        }

        // 跟踪最终 assistant Msg（来自 AgentResultEvent）
        AtomicReference<Msg> lastResult = new AtomicReference<>();

        return next.apply(input)
                .doOnNext(
                        event -> {
                            if (event instanceof AgentResultEvent r && r.getResult() != null) {
                                lastResult.set(r.getResult());
                            }
                        })
                .doOnComplete(
                        () -> {
                            // 流正常结束时把 assistant 最终 Msg 写入
                            Msg result = lastResult.get();
                            if (conversationId != null && result != null) {
                                writeAssistantMessage(conversationId, assistantId, result);
                            }
                        });
    }

    private void writeUserMessage(Long conversationId, Long userId, Msg msg) {
        try {
            String text = msg.getTextContent();
            if (text == null || text.isBlank()) return; // 不写空消息
            jdbc.update(
                    """
                    INSERT INTO conversation_message
                        (conversation_id, sender_id, sender_type, role, content, content_type, create_time)
                    VALUES (?, ?, 'HUMAN', 'user', ?, 'TEXT', CURRENT_TIMESTAMP)
                    """,
                    conversationId,
                    userId == null ? null : String.valueOf(userId),
                    text);
            log.debug(
                    "[ConvBridge] user msg saved conversationId={} userId={} len={}",
                    conversationId,
                    userId,
                    text.length());
        } catch (Exception e) {
            log.warn(
                    "[ConvBridge] 写入 user 消息失败 conversationId={} userId={}: {}",
                    conversationId,
                    userId,
                    e.getMessage());
        }
    }

    private void writeAssistantMessage(Long conversationId, Long assistantId, Msg msg) {
        try {
            String text = msg.getTextContent();
            if (text == null || text.isBlank()) return;
            jdbc.update(
                    """
                    INSERT INTO conversation_message
                        (conversation_id, sender_id, sender_type, role, content, content_type, create_time)
                    VALUES (?, ?, 'ASSISTANT', 'assistant', ?, 'TEXT', CURRENT_TIMESTAMP)
                    """,
                    conversationId,
                    assistantId == null ? null : String.valueOf(assistantId),
                    text);
            log.debug(
                    "[ConvBridge] assistant msg saved conversationId={} assistantId={} len={}",
                    conversationId,
                    assistantId,
                    text.length());
        } catch (Exception e) {
            log.warn(
                    "[ConvBridge] 写入 assistant 消息失败 conversationId={} assistantId={}: {}",
                    conversationId,
                    assistantId,
                    e.getMessage());
        }
    }
}
