package com.xuejiai.aaf.framework.intelligent.agent.runtime;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Agent 事件总线：发布/订阅 + 点对点消息传递。
 * （迁移自 intelligent/agent/AgentEventBus）
 */
@Slf4j
@Component
public class AgentEventBus {

    private final Map<String, List<Consumer<AgentMessage>>> subscribers = new ConcurrentHashMap<>();

    public void publish(String topic, AgentMessage message) {
        var listeners = subscribers.get(topic);
        if (listeners != null) {
            listeners.forEach(listener -> {
                try {
                    listener.accept(message);
                } catch (Exception e) {
                    log.warn("事件处理异常 [topic={}]: {}", topic, e.getMessage());
                }
            });
        }
    }

    public void subscribe(String topic, Consumer<AgentMessage> listener) {
        subscribers.computeIfAbsent(topic, k -> new CopyOnWriteArrayList<>()).add(listener);
    }

    public void unsubscribe(String topic, Consumer<AgentMessage> listener) {
        var listeners = subscribers.get(topic);
        if (listeners != null) listeners.remove(listener);
    }

    public void send(String targetAgentId, AgentMessage message) {
        publish("agent:" + targetAgentId, message);
    }

    public record AgentMessage(
        String fromAgentId, String toAgentId, String type,
        String content, Map<String, Object> metadata
    ) {
        public static AgentMessage of(String from, String to, String content) {
            return new AgentMessage(from, to, "text", content, Map.of());
        }
    }
}
