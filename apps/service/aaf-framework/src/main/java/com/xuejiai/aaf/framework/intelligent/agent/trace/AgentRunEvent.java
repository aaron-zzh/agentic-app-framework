package com.xuejiai.aaf.framework.intelligent.agent.trace;

import java.time.Instant;
import java.util.Map;

/** Agent 运行状态事件。 */
public record AgentRunEvent(
        String runId,
        Long userId,
        String agentId,
        AgentRunEventType type,
        String title,
        String message,
        Map<String, Object> payload,
        Instant timestamp) {

    public static AgentRunEvent of(
            String runId,
            Long userId,
            String agentId,
            AgentRunEventType type,
            String title,
            String message,
            Map<String, Object> payload) {
        return new AgentRunEvent(
                runId,
                userId,
                agentId,
                type,
                title,
                message,
                payload != null ? Map.copyOf(payload) : Map.of(),
                Instant.now());
    }
}
