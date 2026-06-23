package com.xuejiai.aaf.framework.intelligent.assistant;

import org.springframework.stereotype.Service;

import com.xuejiai.aaf.framework.intelligent.core.assistant.AssistantExecutor;

/** AssistantExecutor stub——v1 实现已归档，待对接新 agentscope 路径。 */
@Service
public class DefaultAssistantExecutor implements AssistantExecutor {

    @Override
    public AssistantResponse chat(
            String sessionId, String assistantId, Long userId, String userMessage) {
        throw new UnsupportedOperationException("DefaultAssistantExecutor 待重新实现（v1 已归档）");
    }
}
