/**
 * Agent 调度服务。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.framework.intelligent.assistant;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.PriorityBlockingQueue;

import org.springframework.stereotype.Service;

import com.xuejiai.aaf.framework.intelligent.agent.*;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Agent 调度：按意图路由到合适的 Agent，支持优先级队列。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentDispatcher {

    private final AgentRegistryService registryService;
    private final AgentFactory agentFactory;
    private final AgentSandbox sandbox;

    /**
     * 根据意图调度 Agent 执行。
     *
     * @param intent 意图类型（对应 Agent 的 capability）
     * @param input 用户输入
     * @return Agent 响应
     */
    public Mono<Msg> dispatch(String intent, Msg input) {
        var candidates = registryService.findByCapability(intent);
        if (candidates.isEmpty()) {
            // 降级到默认 Agent
            candidates = registryService.listActive();
        }
        if (candidates.isEmpty()) {
            return Mono.just(Msg.builder().name("system").textContent("无可用 Agent").build());
        }

        // 选择第一个匹配的 Agent
        var definition = candidates.getFirst();
        var agent = agentFactory.create(definition);
        var timeout = Duration.ofSeconds(definition.getTimeoutSeconds());

        log.info("调度 Agent [{}] 处理意图 [{}]", definition.getName(), intent);
        return sandbox.execute(agent, input, timeout);
    }

    /** 批量调度（多 Agent 并行） */
    public List<Mono<Msg>> dispatchMultiple(List<String> intents, Msg input) {
        return intents.stream()
                .map(intent -> dispatch(intent, input))
                .toList();
    }
}
