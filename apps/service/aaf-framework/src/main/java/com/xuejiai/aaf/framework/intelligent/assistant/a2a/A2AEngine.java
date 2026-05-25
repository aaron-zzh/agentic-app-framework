package com.xuejiai.aaf.framework.intelligent.assistant.a2a;

import java.util.List;
import java.util.Map;

/**
 * A2A 协议引擎接口——Assistant 对外通信的统一抽象。
 *
 * <p>两种实现可按需切换：
 * <ul>
 *   <li>{@code LocalA2AEngine} — AAF 自研（内存直调，适合单体部署）</li>
 *   <li>{@code AgentScopeA2AEngine} — 封装 AgentScope A2A（标准协议，适合分布式）</li>
 * </ul>
 *
 * <p>切换方式：通过 Spring Profile 或配置 {@code aaf.a2a.engine=local|agentscope}。
 */
public interface A2AEngine {

    /** 暴露本地 Assistant 为 A2A 可达。 */
    void expose(String assistantId, AgentCard card);

    /** 向目标 Assistant 发送消息并获取响应。 */
    A2AResponse send(String targetAssistantId, A2ARequest request);

    /** 按能力发现可用 Assistant。 */
    List<AgentCard> discover(String capability);

    /** Agent 名片（A2A 标准中的 Agent Card） */
    record AgentCard(
            String assistantId,
            String name,
            String description,
            String url,
            List<String> capabilities) {}

    /** A2A 请求 */
    record A2ARequest(
            String conversationId,
            Long fromUserId,
            String content,
            Map<String, Object> metadata) {}

    /** A2A 响应 */
    record A2AResponse(boolean success, String content, String error) {
        public static A2AResponse success(String content) { return new A2AResponse(true, content, null); }
        public static A2AResponse error(String error) { return new A2AResponse(false, null, error); }
    }
}
