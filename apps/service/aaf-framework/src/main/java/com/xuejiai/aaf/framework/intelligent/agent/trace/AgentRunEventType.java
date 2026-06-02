package com.xuejiai.aaf.framework.intelligent.agent.trace;

/** Agent 运行事件类型。 */
public enum AgentRunEventType {
    RUN_STARTED,
    RUN_FINISHED,
    RUN_ERROR,
    TOOL_CALL_STARTED,
    TOOL_CALL_COMPLETED,
    TOOL_CALL_FAILED,
    ROLE_SWITCHED,
    SUB_AGENT_STARTED,
    SUB_AGENT_COMPLETED,
    COORDINATION_STARTED,
    COORDINATION_DECISION
}
