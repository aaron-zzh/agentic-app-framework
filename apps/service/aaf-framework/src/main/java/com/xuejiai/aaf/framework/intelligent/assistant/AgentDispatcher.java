/**
 * Agent 调度服务。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.framework.intelligent.assistant;

import java.time.Duration;
import java.util.List;

import org.springframework.stereotype.Service;

import com.xuejiai.aaf.framework.intelligent.agent.AgentFactory;
import com.xuejiai.aaf.framework.intelligent.agent.AgentRegistryService;
import com.xuejiai.aaf.framework.intelligent.agent.runtime.AgentSandbox;
import com.xuejiai.aaf.framework.intelligent.core.agent.AgentExecutor;
import com.xuejiai.aaf.framework.intelligent.core.token.CreditService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Agent 调度：按意图路由到合适的 Agent，支持优先级队列。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentDispatcher {

    private final AgentRegistryService registryService;
    private final AgentFactory agentFactory;
    private final AgentSandbox sandbox;
    private final CreditService creditService;

    /**
     * 根据意图调度 Agent 执行。
     *
     * @param intent 意图类型（对应 Agent 的 capability）
     * @param input 用户输入文本
     * @return Agent 执行结果
     */
    public AgentExecutor.AgentResult dispatch(String intent, String input) {
        // 预算检查（P1 占位：当前 DefaultCreditService 始终放行）
        // TODO: 从上下文获取 userId，超额时降级到便宜模型
        var candidates = registryService.findByCapability(intent);
        if (candidates.isEmpty()) {
            candidates = registryService.listActive();
        }
        if (candidates.isEmpty()) {
            return AgentExecutor.AgentResult.error("无可用 Agent");
        }

        var definition = candidates.getFirst();
        var agent = agentFactory.create(definition);
        var timeout = Duration.ofSeconds(definition.getTimeoutSeconds());

        log.info("调度 Agent [{}] 处理意图 [{}]", definition.getName(), intent);
        return sandbox.execute(agent, input, timeout);
    }

    /** 批量调度（多 Agent 并行） */
    public List<AgentExecutor.AgentResult> dispatchMultiple(List<String> intents, String input) {
        return intents.stream().map(intent -> dispatch(intent, input)).toList();
    }
}
