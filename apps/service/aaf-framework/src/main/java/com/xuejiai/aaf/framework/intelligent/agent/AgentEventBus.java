/**
 * Agent 事件总线。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.framework.intelligent.agent;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Agent 间消息传递和事件总线。
 * 支持发布/订阅模式，Agent 可以订阅特定主题接收消息。
 */
@Slf4j
@Component
public class AgentEventBus {

    private final Map<String, List<Consumer<AgentMessage>>> subscribers = new ConcurrentHashMap<>();

    /** 发布消息到指定主题 */
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

    /** 订阅主题 */
    public void subscribe(String topic, Consumer<AgentMessage> listener) {
        subscribers.computeIfAbsent(topic, k -> new CopyOnWriteArrayList<>()).add(listener);
    }

    /** 取消订阅 */
    public void unsubscribe(String topic, Consumer<AgentMessage> listener) {
        var listeners = subscribers.get(topic);
        if (listeners != null) {
            listeners.remove(listener);
        }
    }

    /** 点对点发送 */
    public void send(String targetAgentId, AgentMessage message) {
        publish("agent:" + targetAgentId, message);
    }

    /** Agent 间消息 */
    public record AgentMessage(
            String fromAgentId,
            String toAgentId,
            String type,
            String content,
            Map<String, Object> metadata) {

        public static AgentMessage of(String from, String to, String content) {
            return new AgentMessage(from, to, "text", content, Map.of());
        }
    }
}
