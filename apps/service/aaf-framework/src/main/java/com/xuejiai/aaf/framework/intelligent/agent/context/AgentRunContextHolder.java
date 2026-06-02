package com.xuejiai.aaf.framework.intelligent.agent.context;

import java.util.Optional;

/** Agent 运行上下文持有器。 */
public final class AgentRunContextHolder {

    private static final ThreadLocal<AgentRunContext> CONTEXT = new ThreadLocal<>();

    private AgentRunContextHolder() {}

    public static Optional<AgentRunContext> current() {
        return Optional.ofNullable(CONTEXT.get());
    }

    public static Scope open(String runId, Long userId, String agentId) {
        return open(runId, userId, agentId, null, null, null);
    }

    public static Scope open(
            String runId,
            Long userId,
            String agentId,
            String assistantId,
            String conversationId,
            Long knowledgeBaseId) {
        var previous = CONTEXT.get();
        CONTEXT.set(
                new AgentRunContext(
                        runId, userId, agentId, assistantId, conversationId, knowledgeBaseId));
        return new Scope(previous);
    }

    /** 直接设置上下文（无 Scope，适用于 Hook 等无法使用 try-with-resources 的场景）。 */
    public static void set(
            String runId,
            Long userId,
            String agentId,
            String assistantId,
            String conversationId,
            Long knowledgeBaseId) {
        CONTEXT.set(
                new AgentRunContext(
                        runId, userId, agentId, assistantId, conversationId, knowledgeBaseId));
    }

    public static void clear() {
        CONTEXT.remove();
    }

    public static final class Scope implements AutoCloseable {
        private final AgentRunContext previous;

        private Scope(AgentRunContext previous) {
            this.previous = previous;
        }

        @Override
        public void close() {
            if (previous == null) {
                CONTEXT.remove();
            } else {
                CONTEXT.set(previous);
            }
        }
    }
}
