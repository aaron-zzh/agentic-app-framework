package com.xuejiai.aaf.module.ai.a2a;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.xuejiai.aaf.framework.intelligent.assistant.a2a.A2AEngine;
import com.xuejiai.aaf.framework.intelligent.assistant.a2a.A2AEngine.A2ARequest;
import com.xuejiai.aaf.framework.intelligent.assistant.a2a.A2AEngine.A2AResponse;
import com.xuejiai.aaf.framework.intelligent.assistant.a2a.A2AEngine.AgentCard;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * A2A 协议服务——交互层入口，委托给 A2AEngine 实现
 *
 * <p>职责：权限校验 + 参数转换 + 委托引擎。 底层引擎可通过配置切换（local / agentscope）。
 *
 * @author AaronZZH & Kiro
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class A2AProtocolService {

    private final A2AEngine a2aEngine;

    /**
     * 暴露本地 Assistant 为 A2A 可达
     *
     * @param assistantId 助手 ID
     * @param name 助手名称
     * @param description 助手描述
     * @param capabilities 能力列表
     */
    public void expose(
            String assistantId, String name, String description, List<String> capabilities) {
        a2aEngine.expose(
                assistantId, new AgentCard(assistantId, name, description, null, capabilities));
    }

    /**
     * 接收 A2A 请求
     *
     * @param targetAssistantId 目标助手 ID
     * @param conversationId 会话 ID
     * @param fromUserId 发送方用户 ID
     * @param content 消息内容
     * @return A2A 响应
     */
    public A2AResponse receive(
            String targetAssistantId, String conversationId, Long fromUserId, String content) {
        return a2aEngine.send(
                targetAssistantId, new A2ARequest(conversationId, fromUserId, content, Map.of()));
    }

    /**
     * 发送 A2A 请求到目标 Assistant
     *
     * @param targetAssistantId 目标助手 ID
     * @param conversationId 会话 ID
     * @param fromUserId 发送方用户 ID
     * @param content 消息内容
     * @return A2A 响应
     */
    public A2AResponse send(
            String targetAssistantId, String conversationId, Long fromUserId, String content) {
        return a2aEngine.send(
                targetAssistantId, new A2ARequest(conversationId, fromUserId, content, Map.of()));
    }

    /**
     * 按能力发现可用 Assistant
     *
     * @param capability 能力关键字
     * @return 匹配的 AgentCard 列表
     */
    public List<AgentCard> discover(String capability) {
        return a2aEngine.discover(capability);
    }
}
