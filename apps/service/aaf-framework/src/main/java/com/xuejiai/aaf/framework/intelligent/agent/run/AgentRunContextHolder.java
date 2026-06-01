package com.xuejiai.aaf.framework.intelligent.agent.run;

import java.util.Optional;

/** Agent 运行上下文持有器。 */
public final class AgentRunContextHolder {

    private static final ThreadLocal<AgentRunContext> CONTEXT = new ThreadLocal<>();

    private AgentRunContextHolder() {}

    public static Optional<AgentRunContext> current() {
        return Optional.ofNullable(CONTEXT.get());
    }

    public static Scope open(String runId, Long userId, String agentId) {
        var previous = CONTEXT.get();
        CONTEXT.set(new AgentRunContext(runId, userId, agentId));
        return new Scope(previous);
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
