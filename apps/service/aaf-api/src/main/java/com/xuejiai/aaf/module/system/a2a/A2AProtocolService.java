package com.xuejiai.aaf.module.system.a2a;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.xuejiai.aaf.framework.intelligent.assistant.AssistantService;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * A2A 协议服务——Assistant 层对外提供的 Agent-to-Agent 通信能力。
 *
 * <p>设计定位：
 * <ul>
 *   <li>A2A 是 Assistant 的能力，不是 Team 的能力</li>
 *   <li>每个 Assistant 可对外暴露 A2A 端点，接收其他 Assistant 的请求</li>
 *   <li>Team 层的协调者（coordinator）通过 A2A 与团队成员 Assistant 通信</li>
 * </ul>
 *
 * <p>协议流程：
 * <pre>
 * 外部 Assistant → A2A 请求 → 本地 Assistant → AssistantService.handle() → 响应
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class A2AProtocolService {

    private final AssistantService assistantService;

    /** 已注册的本地 Assistant A2A 端点 */
    private final Map<String, AssistantEndpoint> localEndpoints = new ConcurrentHashMap<>();

    /** 已发现的远程 Assistant 端点 */
    private final Map<String, AssistantEndpoint> remoteEndpoints = new ConcurrentHashMap<>();

    /** 注册本地 Assistant 为 A2A 可达端点。 */
    public void expose(String assistantId, List<String> capabilities) {
        localEndpoints.put(assistantId, new AssistantEndpoint(assistantId, null, capabilities));
        log.info("A2A 暴露本地 Assistant: {} capabilities={}", assistantId, capabilities);
    }

    /** 注册远程 Assistant 端点。 */
    public void registerRemote(String assistantId, String url, List<String> capabilities) {
        remoteEndpoints.put(assistantId, new AssistantEndpoint(assistantId, url, capabilities));
        log.info("A2A 注册远程 Assistant: {} -> {}", assistantId, url);
    }

    /**
     * 接收 A2A 请求——委托给本地 AssistantService 处理。
     *
     * @param targetAssistantId 目标 Assistant ID
     * @param message A2A 消息
     * @return 响应
     */
    public A2AResponse receive(String targetAssistantId, A2AMessage message) {
        if (!localEndpoints.containsKey(targetAssistantId)) {
            return A2AResponse.error("目标 Assistant 未暴露 A2A 端点: " + targetAssistantId);
        }
        // 委托给 AssistantService 完整链路处理
        var response = assistantService.handle(
                "a2a:" + message.conversationId(),
                message.fromUserId(),
                targetAssistantId,
                message.content());
        return A2AResponse.success(response.content());
    }

    /**
     * 发送 A2A 请求到远程 Assistant。
     *
     * @param targetAssistantId 远程 Assistant ID
     * @param message 消息
     * @return 响应
     */
    public A2AResponse send(String targetAssistantId, A2AMessage message) {
        var endpoint = remoteEndpoints.get(targetAssistantId);
        if (endpoint == null) {
            return A2AResponse.error("远程 Assistant 未注册: " + targetAssistantId);
        }
        // TODO: HTTP 调用远程 A2A 端点
        log.info("A2A 发送到远程 [{}]: {}", targetAssistantId, message.content());
        return A2AResponse.error("远程调用尚未实现");
    }

    /** 按能力发现可用 Assistant（本地+远程）。 */
    public List<AssistantEndpoint> discover(String capability) {
        var all = new java.util.ArrayList<>(localEndpoints.values());
        all.addAll(remoteEndpoints.values());
        return all.stream()
                .filter(e -> e.capabilities().contains(capability))
                .toList();
    }

    /** Assistant A2A 端点 */
    public record AssistantEndpoint(String assistantId, String url, List<String> capabilities) {}

    /** A2A 消息 */
    public record A2AMessage(String conversationId, Long fromUserId, String content, Map<String, Object> metadata) {}

    /** A2A 响应 */
    public record A2AResponse(boolean success, String error, String content) {
        public static A2AResponse success(String content) { return new A2AResponse(true, null, content); }
        public static A2AResponse error(String error) { return new A2AResponse(false, error, null); }
    }
}
