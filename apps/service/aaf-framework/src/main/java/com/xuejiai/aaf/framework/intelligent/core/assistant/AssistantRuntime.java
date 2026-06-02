package com.xuejiai.aaf.framework.intelligent.core.assistant;

import io.agentscope.core.ReActAgent;

/**
 * 助理运行时——将 AssistantDefinition 物化为可执行的协调者 Agent。
 *
 * <p>这是两条入口（AG-UI 流式 + AssistantService 完整输出）共享的统一构建逻辑。 物化后的 ReActAgent 内置完整 Hook 链，调用方只需 {@code
 * agent.stream(msg)} 或 {@code agent.call(msg)}。
 */
public interface AssistantRuntime {

    /**
     * 按上下文物化协调者 Agent。
     *
     * @param ctx 物化上下文（assistantId、userId、threadId、knowledgeBaseId 等）
     * @return 完整配置的 ReActAgent（含 Hook、Toolkit、Memory、SkillBox）
     */
    ReActAgent materialize(MaterializeContext ctx);

    /** 物化上下文 */
    record MaterializeContext(
            String assistantId, Long userId, String threadId, Long knowledgeBaseId) {}
}
