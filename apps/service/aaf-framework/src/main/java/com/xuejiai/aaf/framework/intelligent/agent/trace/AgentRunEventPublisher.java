package com.xuejiai.aaf.framework.intelligent.agent.trace;

import java.util.Map;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import com.xuejiai.aaf.framework.intelligent.agent.context.AgentRunContext;
import com.xuejiai.aaf.framework.intelligent.agent.context.AgentRunContextHolder;

import lombok.RequiredArgsConstructor;

/** Agent 运行事件发布器。 */
@Service
@RequiredArgsConstructor
public class AgentRunEventPublisher {

    private final ApplicationEventPublisher eventPublisher;

    public void publish(
            AgentRunEventType type, String title, String message, Map<String, Object> payload) {
        AgentRunContextHolder.current()
                .ifPresent(ctx -> publish(ctx, type, title, message, payload));
    }

    public void publish(
            AgentRunContext context,
            AgentRunEventType type,
            String title,
            String message,
            Map<String, Object> payload) {
        eventPublisher.publishEvent(
                AgentRunEvent.of(
                        context.runId(),
                        context.userId(),
                        context.agentId(),
                        type,
                        title,
                        message,
                        payload));
    }
}
