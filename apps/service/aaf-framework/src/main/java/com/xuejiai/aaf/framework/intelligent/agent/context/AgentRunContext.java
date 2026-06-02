package com.xuejiai.aaf.framework.intelligent.agent.context;

/** 当前线程内 Agent 运行上下文。 */
public record AgentRunContext(
        String runId,
        Long userId,
        String agentId,
        String assistantId,
        String conversationId,
        Long knowledgeBaseId) {}
