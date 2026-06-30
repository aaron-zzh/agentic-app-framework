/*
 * Copyright 2024-2026 xuejiai.com & AaronZZH.
 * Licensed under the Apache License, Version 2.0.
 */
package com.xuejiai.aaf.framework.agentscope.middleware;

import java.util.List;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.agentscope.runtime.AafContextHolder;
import com.xuejiai.aaf.framework.agentscope.runtime.AgentCapabilityContext;
import com.xuejiai.aaf.framework.engine.credit.AiCreditGuard;
import com.xuejiai.aaf.framework.intelligent.core.AiUsage;
import com.xuejiai.aaf.framework.intelligent.core.model.AiModelRepository;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.ModelCallEndEvent;
import io.agentscope.core.event.ToolCallEndEvent;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.middleware.ActingInput;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.middleware.ModelCallInput;
import io.agentscope.core.model.ChatUsage;
import reactor.core.publisher.Flux;

/**
 * 调用日志中间件——在 token 粒度持久化 LLM 调用与工具调用记录。
 *
 * <ul>
 *   <li>{@link #onModelCall}：在事件流中拦截 {@link ModelCallEndEvent}，写入 {@code ai_llm_call_log}
 *   <li>{@link #onActing}：在工具调用开始时写入 {@code ai_tool_call_log}（status=STARTED）， 调用完成后更新
 *       status=COMPLETED
 * </ul>
 *
 * <p>与 {@link ConversationBridgeMiddleware} 同模式——直接用 {@link JdbcTemplate} 写表，无需 JPA 上下文。
 */
public class CallLogMiddleware implements MiddlewareBase {

    private static final Logger log = LoggerFactory.getLogger(CallLogMiddleware.class);

    private final JdbcTemplate jdbc;
    private final AiCreditGuard creditGuard;
    private final AiModelRepository modelRepository;

    public CallLogMiddleware(
            JdbcTemplate jdbc, AiCreditGuard creditGuard, AiModelRepository modelRepository) {
        this.jdbc = jdbc;
        this.creditGuard = creditGuard;
        this.modelRepository = modelRepository;
    }

    // ---- onModelCall：捕获 ModelCallEndEvent 写 ai_llm_call_log ----

    @Override
    public Flux<AgentEvent> onModelCall(
            Agent agent,
            RuntimeContext ctx,
            ModelCallInput input,
            Function<ModelCallInput, Flux<AgentEvent>> next) {

        Long userId = AafContextHolder.userId();
        String threadId = AafContextHolder.threadId();
        Long conversationId = AafContextHolder.conversationId();
        Long assistantId = AafContextHolder.assistantId();
        String modelName = input.model().getClass().getSimpleName();

        // 通过 assistantId 提前查好 modelId（有缓存），避免 doOnNext 里重复查库
        String resolvedModelId = resolveModelId(assistantId);

        return next.apply(input)
                .doOnNext(
                        event -> {
                            if (event instanceof ModelCallEndEvent e) {
                                ChatUsage usage = e.getUsage();
                                if (usage == null) return;
                                writeLlmLog(
                                        conversationId,
                                        userId,
                                        assistantId,
                                        threadId,
                                        modelName,
                                        e.getReplyId(),
                                        usage.getInputTokens(),
                                        usage.getOutputTokens(),
                                        usage.getTotalTokens(),
                                        usage.getTime());
                                // 积分结算：按 token 扣减
                                settleCredits(
                                        userId,
                                        resolvedModelId,
                                        usage.getInputTokens(),
                                        usage.getOutputTokens());
                            }
                        });
    }

    // ---- onActing：写入工具调用入参，完成后更新 status ----

    @Override
    public Flux<AgentEvent> onActing(
            Agent agent,
            RuntimeContext ctx,
            ActingInput input,
            Function<ActingInput, Flux<AgentEvent>> next) {

        Long userId = AafContextHolder.userId();
        String threadId = AafContextHolder.threadId();
        Long conversationId = AafContextHolder.conversationId();

        List<ToolUseBlock> toolCalls = input.toolCalls();
        if (toolCalls != null) {
            for (ToolUseBlock tc : toolCalls) {
                writeToolLog(conversationId, userId, threadId, tc, "STARTED");
            }
        }

        return next.apply(input)
                .doOnNext(
                        event -> {
                            if (event instanceof ToolCallEndEvent e) {
                                updateToolLogStatus(e.getToolCallId(), "COMPLETED");
                            }
                        });
    }

    // ---- 写库辅助方法 ----

    private void writeLlmLog(
            Long conversationId,
            Long userId,
            Long assistantId,
            String threadId,
            String modelName,
            String replyId,
            int inputTokens,
            int outputTokens,
            int totalTokens,
            double durationSeconds) {
        try {
            jdbc.update(
                    """
                    INSERT INTO ai_llm_call_log
                        (conversation_id, user_id, assistant_id, thread_id,
                         model_name, reply_id,
                         input_tokens, output_tokens, total_tokens, duration_seconds,
                         create_time, update_time)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """,
                    conversationId,
                    userId,
                    assistantId,
                    threadId,
                    modelName,
                    replyId,
                    inputTokens,
                    outputTokens,
                    totalTokens,
                    durationSeconds);
            log.debug(
                    "[CallLog] LLM log saved threadId={} inputTokens={} outputTokens={}",
                    threadId,
                    inputTokens,
                    outputTokens);
        } catch (Exception e) {
            log.warn("[CallLog] 写入 LLM 调用日志失败 threadId={}: {}", threadId, e.getMessage());
        }
    }

    private void writeToolLog(
            Long conversationId, Long userId, String threadId, ToolUseBlock tc, String status) {
        try {
            String inputJson = toJson(tc.getInput());
            jdbc.update(
                    """
                    INSERT INTO ai_tool_call_log
                        (conversation_id, user_id, thread_id,
                         tool_call_id, tool_name, tool_input, status,
                         create_time, update_time)
                    VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """,
                    conversationId,
                    userId,
                    threadId,
                    tc.getId(),
                    tc.getName(),
                    inputJson,
                    status);
            log.debug(
                    "[CallLog] tool log saved threadId={} tool={} status={}",
                    threadId,
                    tc.getName(),
                    status);
        } catch (Exception e) {
            log.warn(
                    "[CallLog] 写入工具调用日志失败 threadId={} tool={}: {}",
                    threadId,
                    tc.getName(),
                    e.getMessage());
        }
    }

    private void updateToolLogStatus(String toolCallId, String status) {
        if (toolCallId == null) return;
        try {
            jdbc.update(
                    """
                    UPDATE ai_tool_call_log SET status = ?, update_time = CURRENT_TIMESTAMP
                    WHERE tool_call_id = ? AND status = 'STARTED' AND deleted = FALSE
                    """,
                    status,
                    toolCallId);
        } catch (Exception e) {
            log.warn("[CallLog] 更新工具调用状态失败 toolCallId={}: {}", toolCallId, e.getMessage());
        }
    }

    /** 解析 modelId：优先用前端传入的 modelId，其次查 ai_assistant.model_id。 */
    private String resolveModelId(Long assistantId) {
        // 1. 前端显式指定（forwardedProps.modelId）
        String ctxModelId = AafContextHolder.modelId();
        if (ctxModelId != null && !ctxModelId.isBlank()) return ctxModelId;
        // 2. 查 ai_assistant 默认模型
        if (assistantId == null) return null;
        try {
            return jdbc.queryForObject(
                    "SELECT model_id FROM ai_assistant WHERE id = ? AND deleted = false LIMIT 1",
                    String.class,
                    assistantId);
        } catch (Exception e) {
            log.debug(
                    "[CallLog] 查 assistant modelId 失败 assistantId={}: {}",
                    assistantId,
                    e.getMessage());
            return null;
        }
    }

    /** 按 token 结算积分——用 modelId 查 AiModel 定价后调 settleByUsage。 */
    private void settleCredits(Long userId, String modelId, int inputTokens, int outputTokens) {
        if (userId == null || creditGuard == null) return;
        try {
            var aiModel =
                    modelId != null ? modelRepository.findByModelId(modelId).orElse(null) : null;
            final long in = inputTokens;
            final long out = outputTokens;
            AiUsage usage =
                    new AiUsage() {
                        @Override
                        public java.util.Map<String, Object> standardUsage() {
                            return java.util.Map.of("inputTokens", in, "outputTokens", out);
                        }
                    };
            String capability =
                    AgentCapabilityContext.get() != null ? AgentCapabilityContext.get() : "chat";
            creditGuard.settleByUsage(userId, aiModel, usage, capability, "AI 对话");
            log.debug(
                    "[CallLog] 积分结算完成 userId={} modelId={} in={} out={}",
                    userId,
                    modelId,
                    inputTokens,
                    outputTokens);
        } catch (Exception e) {
            log.warn("[CallLog] 积分结算失败 userId={} modelId={}: {}", userId, modelId, e.getMessage());
        }
    }

    private String toJson(Object obj) {
        if (obj == null) return "{}";
        try {
            return JsonUtils.toJsonString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }
}
