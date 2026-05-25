package com.xuejiai.aaf.framework.intelligent.agent.trace;

/** 执行步骤类型。 */
public enum StepType {
    PERCEIVE,
    PLAN,
    TOOL_CALL,
    LLM_CALL,
    EVALUATE,
    LEARN
}
