package com.xuejiai.aaf.framework.intelligent.agent.runtime;

import java.time.Duration;

import org.springframework.stereotype.Service;

import com.xuejiai.aaf.framework.intelligent.agent.AgentDefinition;
import com.xuejiai.aaf.framework.intelligent.core.memory.MemoryStrategy;

/** CognitiveCycleExecutor stub——v1 实现已归档，待对接新 agentscope 路径。 */
@Service
public class CognitiveCycleExecutor {

    public CycleResult execute(AgentDefinition definition, String input, Long userId) {
        throw new UnsupportedOperationException("CognitiveCycleExecutor 待重新实现（v1 已归档）");
    }

    public CycleResult execute(
            AgentDefinition definition,
            String input,
            Long userId,
            String conversationId,
            MemoryStrategy memoryStrategy,
            Long knowledgeBaseId) {
        throw new UnsupportedOperationException("CognitiveCycleExecutor 待重新实现（v1 已归档）");
    }

    public record CycleResult(String response, boolean success, Duration duration, int memoryItemsUsed) {}
}
