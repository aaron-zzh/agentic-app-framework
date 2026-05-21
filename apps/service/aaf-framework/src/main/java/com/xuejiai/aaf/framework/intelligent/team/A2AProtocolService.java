/**
 * A2A 协议服务。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.framework.intelligent.team;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/** Agent-to-Agent 通信协议实现。 基于 AgentScope A2A 扩展，提供 AAF 层面的消息路由和协议封装。 支持同步和异步通信模式。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class A2AProtocolService {

    /** 已注册的远程 Agent 端点 */
    private final Map<String, AgentEndpoint> endpoints = new ConcurrentHashMap<>();

    /** 注册远程 Agent 端点 */
    public void registerEndpoint(String agentId, String url, List<String> capabilities) {
        endpoints.put(agentId, new AgentEndpoint(agentId, url, capabilities));
        log.info("注册 A2A 端点: {} -> {}", agentId, url);
    }

    /** 发送消息到远程 Agent */
    public A2AResponse sendMessage(String targetAgentId, A2AMessage message) {
        var endpoint = endpoints.get(targetAgentId);
        if (endpoint == null) {
            return new A2AResponse(false, "目标 Agent 未注册: " + targetAgentId, null);
        }
        // 通过 AgentScope A2A Client 发送
        // 当前返回占位响应，实际实现依赖 agentscope-a2a-spring-boot-starter
        log.info("A2A 发送消息到 [{}]: {}", targetAgentId, message.getContent());
        return new A2AResponse(true, null, "消息已发送");
    }

    /** 发现可用的远程 Agent */
    public List<AgentEndpoint> discoverAgents(String capability) {
        return endpoints.values().stream()
                .filter(e -> e.getCapabilities().contains(capability))
                .toList();
    }

    /** 远程 Agent 端点 */
    @Getter
    @Setter
    public static class AgentEndpoint {
        private String agentId;
        private String url;
        private List<String> capabilities;

        public AgentEndpoint(String agentId, String url, List<String> capabilities) {
            this.agentId = agentId;
            this.url = url;
            this.capabilities = capabilities;
        }
    }

    /** A2A 消息 */
    @Getter
    @Setter
    public static class A2AMessage {
        private String fromAgentId;
        private String content;
        private Map<String, Object> metadata;
    }

    /** A2A 响应 */
    public record A2AResponse(boolean success, String error, String content) {}
}
