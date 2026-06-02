package com.xuejiai.aaf.framework.intelligent.agent.context;

/** 当前线程内 Agent 运行上下文。 */
public record AgentRunContext(
        String runId,
        Long userId,
        String agentId,
        String assistantId,
        String conversationId,
        Long knowledgeBaseId) {

    /** 仅 runId 的便利构造器（其余字段置 null）。 */
    public AgentRunContext(String runId, Long userId, String agentId) {
        this(runId, userId, agentId, null, null, null);
    }
}
