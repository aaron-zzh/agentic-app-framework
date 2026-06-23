package com.xuejiai.aaf.framework.intelligent.assistant;

import org.springframework.stereotype.Service;

/** AssistantService stub——v1 实现已归档，待对接新 agentscope 路径。 */
@Service
public class AssistantService {

    public Response handle(String sessionId, Long userId, String assistantId, String input) {
        throw new UnsupportedOperationException("AssistantService 待重新实现（v1 已归档）");
    }

    public record Response(String content, String sessionId) {}
}
