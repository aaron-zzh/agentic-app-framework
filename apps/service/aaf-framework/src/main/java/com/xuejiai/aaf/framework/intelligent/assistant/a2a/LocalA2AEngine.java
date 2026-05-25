package com.xuejiai.aaf.framework.intelligent.assistant.a2a;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.xuejiai.aaf.framework.intelligent.assistant.AssistantService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * AAF 自研 A2A 引擎——内存直调，零网络开销。
 *
 * <p>适用场景：单体部署、同一 JVM 内 Assistant 间通信。
 * 收到请求后直接调用 AssistantService.handle()，不走 HTTP。
 */
@Slf4j
@RequiredArgsConstructor
public class LocalA2AEngine implements A2AEngine {

    private final AssistantService assistantService;

    private final Map<String, AgentCard> registry = new ConcurrentHashMap<>();

    @Override
    public void expose(String assistantId, AgentCard card) {
        registry.put(assistantId, card);
        log.info("A2A[Local] 暴露 Assistant: {}", assistantId);
    }

    @Override
    public A2AResponse send(String targetAssistantId, A2ARequest request) {
        if (!registry.containsKey(targetAssistantId)) {
            return A2AResponse.error("目标 Assistant 未注册: " + targetAssistantId);
        }
        try {
            var response = assistantService.handle(
                    "a2a:" + request.conversationId(),
                    request.fromUserId(),
                    targetAssistantId,
                    request.content());
            return A2AResponse.success(response.content());
        } catch (Exception e) {
            log.error("A2A[Local] 调用失败: target={}, error={}", targetAssistantId, e.getMessage());
            return A2AResponse.error(e.getMessage());
        }
    }

    @Override
    public List<AgentCard> discover(String capability) {
        return registry.values().stream()
                .filter(c -> c.capabilities().contains(capability))
                .toList();
    }
}
