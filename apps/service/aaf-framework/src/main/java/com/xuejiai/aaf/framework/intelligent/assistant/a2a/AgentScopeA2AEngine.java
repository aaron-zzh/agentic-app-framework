package com.xuejiai.aaf.framework.intelligent.assistant.a2a;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.xuejiai.aaf.framework.intelligent.assistant.AssistantService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * AgentScope A2A 引擎——封装 agentscope-a2a-spring-boot-starter。
 *
 * <p>适用场景：分布式部署、跨系统 Agent 互通。
 * 对外暴露标准 A2A 协议端点，对内委托 AssistantService 处理。
 *
 * <p>当前实现：本地请求走直调（同 LocalA2AEngine），远程请求通过 AgentScope A2A Client 发送。
 * AgentScope A2A Server 由 starter 自动配置，入站请求通过 TaskHandler 回调到此处。
 */
@Slf4j
@RequiredArgsConstructor
public class AgentScopeA2AEngine implements A2AEngine {

    private final AssistantService assistantService;

    /** 本地已暴露的 Assistant */
    private final Map<String, AgentCard> localRegistry = new ConcurrentHashMap<>();
    /** 远程发现的 Assistant */
    private final Map<String, AgentCard> remoteRegistry = new ConcurrentHashMap<>();

    @Override
    public void expose(String assistantId, AgentCard card) {
        localRegistry.put(assistantId, card);
        // AgentScope A2A Server 自动暴露端点，此处仅记录
        log.info("A2A[AgentScope] 暴露 Assistant: {} url={}", assistantId, card.url());
    }

    @Override
    public A2AResponse send(String targetAssistantId, A2ARequest request) {
        // 本地 Assistant：直调
        if (localRegistry.containsKey(targetAssistantId)) {
            try {
                var response = assistantService.handle(
                        "a2a:" + request.conversationId(),
                        request.fromUserId(),
                        targetAssistantId,
                        request.content());
                return A2AResponse.success(response.content());
            } catch (Exception e) {
                return A2AResponse.error(e.getMessage());
            }
        }

        // 远程 Assistant：通过 AgentScope A2A Client 发送
        var remote = remoteRegistry.get(targetAssistantId);
        if (remote == null) {
            return A2AResponse.error("目标 Assistant 未发现: " + targetAssistantId);
        }

        // TODO: 调用 AgentScope A2AClient.sendTask(remote.url(), ...)
        // 当前 AgentScope A2A Client API 待确认后补全
        log.info("A2A[AgentScope] 发送远程请求: target={}, url={}", targetAssistantId, remote.url());
        return A2AResponse.error("远程 A2A 调用待 AgentScope Client API 确认后实现");
    }

    @Override
    public List<AgentCard> discover(String capability) {
        var all = new java.util.ArrayList<>(localRegistry.values());
        all.addAll(remoteRegistry.values());
        return all.stream()
                .filter(c -> c.capabilities().contains(capability))
                .toList();
    }

    /** 注册远程 Assistant（由服务发现回调）。 */
    public void registerRemote(AgentCard card) {
        remoteRegistry.put(card.assistantId(), card);
        log.info("A2A[AgentScope] 发现远程 Assistant: {} url={}", card.assistantId(), card.url());
    }
}
