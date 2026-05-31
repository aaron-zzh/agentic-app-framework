package com.xuejiai.aaf.framework.intelligent.agent.agentscope;

/** AgentScope 工具执行时传递给 AAF 治理链的会话上下文。 */
public record AafAgentScopeContext(String sessionId, String assistantId) {}
