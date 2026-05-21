/**
 * Agent 认知循环——感知→规划→执行→评估→学习（对齐认知心理学信息加工模型）。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.framework.intelligent.agent;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;

import com.xuejiai.aaf.framework.intelligent.agent.runtime.AgentSandbox;
import com.xuejiai.aaf.framework.intelligent.cognition.memory.MemoryExtractionService;
import com.xuejiai.aaf.framework.intelligent.core.agent.AgentExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Agent 认知循环执行器：包装 AgentScope ReActAgent， 在执行前后注入 AAF 认知能力（记忆检索/写入）。
 *
 * <pre>
 * 感知（Perceive）→ 规划（Plan）→ 执行（Act）→ 评估（Evaluate）→ 学习（Learn）
 *       ↑                                                              │
 *       └──────────────────── 循环 ←───────────────────────────────────┘
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CognitiveCycleExecutor {

    private final AgentFactory agentFactory;
    private final AgentSandbox sandbox;
    private final WorkingMemory workingMemory;
    private final MemoryExtractionService memoryExtraction;
    private final AgentCheckpointService checkpointService;

    /**
     * 执行完整认知循环（带检查点和重试）。
     *
     * @param definition Agent 定义
     * @param input 用户输入
     * @param userId 用户 ID（用于记忆读写）
     * @return Agent 响应
     */
    public CycleResult execute(AgentDefinition definition, String input, Long userId) {
        var agentId = definition.getAgentId() + ":" + Thread.currentThread().threadId();
        var executionId = agentId + ":" + System.nanoTime();
        var startTime = Instant.now();

        try {
            // 1. 感知（Perceive）：从记忆中提取相关上下文
            workingMemory.focus(agentId, input, 5);
            var focus = workingMemory.getFocus(agentId);
            log.debug("[{}] 感知完成，工作记忆 {} 项", definition.getName(), focus.size());

            checkpointService.saveCheckpoint(
                    executionId, 1, buildState(executionId, agentId, "perceive"));

            // 2. 规划 + 执行（Plan + Act）：带重试
            var response =
                    checkpointService.executeWithRetry(
                            executionId,
                            attempt -> {
                                var agent = agentFactory.create(definition);
                                var timeout = Duration.ofSeconds(definition.getTimeoutSeconds());
                                var result = sandbox.execute(agent, input, timeout);
                                return result;
                            });

            checkpointService.saveCheckpoint(
                    executionId, 2, buildState(executionId, agentId, "execute"));

            // 3. 评估（Evaluate）
            var responseText = response != null ? response.output() : "";
            var success =
                    response != null
                            && response.success()
                            && !responseText.contains("执行超时")
                            && !responseText.contains("执行异常");

            // 4. 学习（Learn）：将对话写入记忆系统
            if (userId != null && success) {
                var conversationText = "用户: %s\n助手: %s".formatted(input, responseText);
                memoryExtraction.extractAndStore(userId, conversationText, Instant.now());
            }

            var duration = Duration.between(startTime, Instant.now());
            checkpointService.clearCheckpoint(executionId);

            return new CycleResult(responseText, success, duration, focus.size());
        } catch (Exception e) {
            log.error("[{}] 认知循环失败: {}", definition.getName(), e.getMessage());
            return new CycleResult(
                    "执行失败: " + e.getMessage(),
                    false,
                    Duration.between(startTime, Instant.now()),
                    0);
        } finally {
            workingMemory.release(agentId);
        }
    }

    private AgentCheckpointService.ExecutionState buildState(
            String executionId, String agentId, String step) {
        var state = new AgentCheckpointService.ExecutionState();
        state.setExecutionId(executionId);
        state.setAgentId(agentId);
        state.setCompletedSteps(new java.util.ArrayList<>(List.of(step)));
        return state;
    }

    /** 认知循环执行结果 */
    public record CycleResult(
            String response, boolean success, Duration duration, int memoryItemsUsed) {}
}
