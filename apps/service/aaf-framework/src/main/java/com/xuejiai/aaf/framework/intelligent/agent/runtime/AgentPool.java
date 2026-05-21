package com.xuejiai.aaf.framework.intelligent.agent.runtime;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.intelligent.agent.AgentDefinition;
import com.xuejiai.aaf.framework.intelligent.agent.AgentFactory;
import com.xuejiai.aaf.framework.intelligent.core.agent.AgentExecutor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Agent 池：按 AgentDefinition 池化复用 AgentExecutor 实例。 借出前注入上下文，归还前重置内部状态（防止跨任务污染）。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentPool {

    private final AgentFactory factory;

    /** agentId → 可用实例队列 */
    private final Map<String, Queue<AgentExecutor>> pool = new ConcurrentHashMap<>();

    /** 借出 Agent 实例（无可用实例时新建）。 */
    public AgentExecutor borrow(AgentDefinition definition) {
        var queue =
                pool.computeIfAbsent(definition.getAgentId(), k -> new ConcurrentLinkedQueue<>());
        var executor = queue.poll();
        if (executor == null) {
            executor = factory.create(definition);
            log.debug("AgentPool 新建实例: {}", definition.getAgentId());
        } else {
            log.debug("AgentPool 复用实例: {}", definition.getAgentId());
        }
        return executor;
    }

    /** 归还 Agent 实例（归还前重置内部状态）。 */
    public void release(String agentId, AgentExecutor executor) {
        executor.reset(); // 清空 AgentScope 内部对话历史
        pool.computeIfAbsent(agentId, k -> new ConcurrentLinkedQueue<>()).offer(executor);
        log.debug("AgentPool 归还实例: {}", agentId);
    }

    /** 清空指定 Agent 的所有池化实例。 */
    public void evict(String agentId) {
        pool.remove(agentId);
    }
}
