package com.xuejiai.aaf.framework.intelligent.agent.trace;

import java.time.Instant;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Agent 协作拓扑写入——仅多 Agent 场景（parentExecutionId 非空时）异步写入 Neo4j。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentCollaborationGraphService {

    private final AgentGraphRepository graphRepository;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMPLETION)
    public void onExecutionCompleted(ExecutionCompletedEvent event) {
        if (event.parentExecutionId() == null) {
            return; // 非多 Agent 协作，跳过
        }
        try {
            recordInvocation(event);
        } catch (Exception e) {
            log.warn("Agent 协作拓扑写入失败 [{}]: {}", event.executionId(), e.getMessage());
        }
    }

    private void recordInvocation(ExecutionCompletedEvent event) {
        // 确保调用方节点存在（从 parentExecutionId 推断 caller agentId 需要查 PG）
        // 此处简化：用 event 中的 agentId 作为被调用方，parentExecutionId 作为调用方标识
        var calleeNode = getOrCreateNode(event.agentId(), event.agentName());

        // 由于 parentExecutionId 只是 ID，完整的 caller 信息需从 PG 查
        // 这里记录被调用方节点即可，调用关系在 caller 侧的事件中建立
        log.debug("Agent 协作节点已记录 [{}]", event.agentId());

        // 如果 event 中有 caller 信息（通过 metadata 传递），建立关系
        if (event.metadata() != null && event.metadata().containsKey("callerAgentId")) {
            var callerAgentId = (String) event.metadata().get("callerAgentId");
            var callerNode = getOrCreateNode(callerAgentId, null);
            updateInvocationRelation(callerNode, calleeNode, event);
        }
    }

    private AgentGraphNode getOrCreateNode(String agentId, String name) {
        return graphRepository
                .findByAgentId(agentId)
                .orElseGet(
                        () -> {
                            var node = new AgentGraphNode();
                            node.setAgentId(agentId);
                            node.setName(name);
                            node.setCreatedAt(Instant.now());
                            return graphRepository.save(node);
                        });
    }

    private void updateInvocationRelation(
            AgentGraphNode caller, AgentGraphNode callee, ExecutionCompletedEvent event) {
        var existing =
                caller.getInvocations().stream()
                        .filter(r -> r.getTarget().getAgentId().equals(callee.getAgentId()))
                        .findFirst();

        if (existing.isPresent()) {
            var rel = existing.get();
            var newCount = rel.getCount() + 1;
            var duration =
                    event.finishedAt() != null && event.startedAt() != null
                            ? java.time.Duration.between(event.startedAt(), event.finishedAt())
                                    .toMillis()
                            : 0L;
            rel.setAvgDurationMs((rel.getAvgDurationMs() * rel.getCount() + duration) / newCount);
            rel.setCount(newCount);
            rel.setLastAt(Instant.now());
        } else {
            var rel = new AgentInvocationRelation();
            rel.setTarget(callee);
            rel.setCount(1);
            rel.setLastAt(Instant.now());
            rel.setAvgDurationMs(
                    event.finishedAt() != null && event.startedAt() != null
                            ? java.time.Duration.between(event.startedAt(), event.finishedAt())
                                    .toMillis()
                            : 0L);
            caller.getInvocations().add(rel);
        }
        graphRepository.save(caller);
    }
}
