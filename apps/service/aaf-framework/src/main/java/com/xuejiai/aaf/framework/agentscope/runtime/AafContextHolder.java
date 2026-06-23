/*
 * Copyright 2024-2026 xuejiai.com & AaronZZH.
 * Licensed under the Apache License, Version 2.0.
 */
package com.xuejiai.aaf.framework.agentscope.runtime;

import java.util.concurrent.atomic.AtomicReference;

/**
 * AAF Agent 运行期上下文——携带 user/conversation 等业务信息，供工具读取。
 *
 * <p>设置时机：AG-UI 请求进入后，由请求拦截器（待实现）从 {@code RunAgentInput.forwardedProps} 提取 userId / conversationId
 * / knowledgeBaseId / assistantId 写入本上下文。
 *
 * <p>读取时机：工具方法（{@code @Tool}）在被模型调用时，通过 {@link #userId()} 等便捷方法拿到当前 thread 绑定的上下文。
 *
 * <p>线程模型：HarnessAgent 内部用 reactor 流处理；{@link ThreadLocal} 在工具同步调用时可工作， 跨线程场景需配合 {@code Reactor
 * Hooks.enableAutomaticContextPropagation()}（Phase-3 接入）。
 *
 * <p><b>开发态兜底</b>：通过 {@link #setDevModeFallback(AafContext)} 设置一个全局兜底上下文， 当线程局部上下文未设置时回落到此（仅 dev
 * 用，生产必须留 null）。
 */
public final class AafContextHolder {

    private static final ThreadLocal<AafContext> CTX = new ThreadLocal<>();

    /** Dev mode fallback——仅在 thread-local 未设置时回退使用。生产环境必须保持 null。 */
    private static final AtomicReference<AafContext> DEV_FALLBACK = new AtomicReference<>();

    private AafContextHolder() {}

    /**
     * AG-UI 请求级上下文。
     *
     * @param userId 当前请求的用户 ID（来自 forwardedProps 或 JWT）
     * @param assistantId 助理 ID（用于查 ai_assistant.knowledge_base_id 等配置）
     * @param conversationId 会话 ID（对应 conversation.id）
     * @param knowledgeBaseId 默认绑定的知识库 ID
     * @param threadId AG-UI threadId（与 conversation.thread_id 一致）
     * @param enableThinking 是否开启思考模式（per-thread，来自 forwardedProps）
     * @param thinkingBudget 思考模式 token 预算（null 时使用默认值 8000）
     */
    public record AafContext(
            Long userId,
            Long assistantId,
            Long conversationId,
            Long knowledgeBaseId,
            String threadId,
            Boolean enableThinking,
            Integer thinkingBudget) {

        /** 兼容旧构造器（不传思考模式参数）。 */
        public AafContext(
                Long userId,
                Long assistantId,
                Long conversationId,
                Long knowledgeBaseId,
                String threadId) {
            this(userId, assistantId, conversationId, knowledgeBaseId, threadId, null, null);
        }
    }

    public static void set(AafContext ctx) {
        CTX.set(ctx);
    }

    public static AafContext get() {
        var c = CTX.get();
        return c != null ? c : DEV_FALLBACK.get();
    }

    public static void clear() {
        CTX.remove();
    }

    /**
     * 设置 dev mode 全局兜底上下文。
     *
     * <p>仅在配置 {@code aaf.agentscope.content-creation.dev-mode-user-id} 非空时由 {@code
     * ContentCreationAutoConfiguration} 调用。生产模式应保持 null。
     */
    public static void setDevModeFallback(AafContext fallback) {
        DEV_FALLBACK.set(fallback);
    }

    /** 便捷方法：获取当前 userId（未设置时返回 {@code null}）。 */
    public static Long userId() {
        var c = get();
        return c == null ? null : c.userId();
    }

    /** 便捷方法：获取当前 conversationId。 */
    public static Long conversationId() {
        var c = get();
        return c == null ? null : c.conversationId();
    }

    /** 便捷方法：获取当前知识库 ID。 */
    public static Long knowledgeBaseId() {
        var c = get();
        return c == null ? null : c.knowledgeBaseId();
    }

    /** 便捷方法：获取当前 threadId。 */
    public static String threadId() {
        var c = get();
        return c == null ? null : c.threadId();
    }

    /** 便捷方法：获取当前 assistantId。 */
    public static Long assistantId() {
        var c = get();
        return c == null ? null : c.assistantId();
    }

    /** 便捷方法：当前对话是否开启思考模式。 */
    public static boolean enableThinking() {
        var c = get();
        return c != null && Boolean.TRUE.equals(c.enableThinking());
    }

    /** 便捷方法：获取思考模式 token 预算，未设置时返回默认值 8000。 */
    public static int thinkingBudget() {
        var c = get();
        return (c != null && c.thinkingBudget() != null) ? c.thinkingBudget() : 8000;
    }
}
