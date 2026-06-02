/**
 * Agent 认知循环——感知→规划→执行→评估→学习（对齐认知心理学信息加工模型）。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.framework.intelligent.agent.runtime;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import com.xuejiai.aaf.framework.intelligent.agent.AgentDefinition;
import com.xuejiai.aaf.framework.intelligent.agent.AgentFactory;
import org.springframework.stereotype.Service;

import com.xuejiai.aaf.framework.intelligent.cognition.learning.TrajectoryCollector;
import com.xuejiai.aaf.framework.intelligent.cognition.memory.MemoryExtractionService;
import com.xuejiai.aaf.framework.intelligent.cognition.pipeline.MemoryPipelineFactory;
import com.xuejiai.aaf.framework.intelligent.core.memory.MemoryStrategy;
import com.xuejiai.aaf.framework.intelligent.core.memory.PipelineInput;

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
    private final MemoryPipelineFactory memoryPipelineFactory;
    private final TrajectoryCollector trajectoryCollector;

    /**
     * 执行完整认知循环（带检查点和重试）。
     *
     * @param definition Agent 定义
     * @param input 用户输入
     * @param userId 用户 ID（用于记忆读写）
     * @return Agent 响应
     */
    public CycleResult execute(AgentDefinition definition, String input, Long userId) {
        return execute(definition, input, userId, null, null, null);
    }

    /**
     * 执行完整认知循环（含记忆管道上下文注入）。
     *
     * @param definition Agent 定义
     * @param input 用户输入
     * @param userId 用户 ID
     * @param conversationId 会话 ID（用于短期记忆检索）
     * @param memoryStrategy 记忆策略（null 则用默认 HYBRID）
     * @param knowledgeBaseId 知识库 ID（可为 null）
     * @return Agent 响应
     */
    public CycleResult execute(
            AgentDefinition definition,
            String input,
            Long userId,
            String conversationId,
            MemoryStrategy memoryStrategy,
            Long knowledgeBaseId) {
        var agentId = definition.getAgentId() + ":" + Thread.currentThread().threadId();
        var executionId = agentId + ":" + System.nanoTime();
        var startTime = Instant.now();

        try {
            // 1. 感知（Perceive）：通过 MemoryPipeline 拉取完整上下文
            var pipeline = memoryPipelineFactory.create(memoryStrategy);
            var memoryContext =
                    pipeline.execute(
                            new PipelineInput(input, userId, conversationId, knowledgeBaseId));
            var contextPrompt = memoryContext.toPromptSection();

            workingMemory.focus(agentId, input, 5);
            log.debug(
                    "[{}] 感知完成，记忆上下文 {} tokens", definition.getName(), memoryContext.totalTokens());

            checkpointService.saveCheckpoint(
                    executionId, 1, buildState(executionId, agentId, "perceive"));

            // 2. 规划 + 执行（Plan + Act）：注入记忆上下文到 Agent prompt
            var response =
                    checkpointService.executeWithRetry(
                            executionId,
                            attempt -> {
                                // 注入记忆上下文
                                if (!contextPrompt.isBlank()) {
                                    var enrichedPrompt =
                                            definition.getSystemPrompt() != null
                                                    ? definition.getSystemPrompt()
                                                            + "\n\n## 上下文记忆\n"
                                                            + contextPrompt
                                                    : "## 上下文记忆\n" + contextPrompt;
                                    definition.setSystemPrompt(enrichedPrompt);
                                }
                                var agent = agentFactory.create(definition);
                                var timeout = Duration.ofSeconds(definition.getTimeoutSeconds());
                                return sandbox.execute(agent, input, timeout);
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

            // 5. 轨迹采集（异步持久化）
            collectTrajectory(executionId, agentId, userId, input, responseText, success, duration);

            return new CycleResult(responseText, success, duration, memoryContext.totalTokens());
        } catch (Exception e) {
            log.error("[{}] 认知循环失败: {}", definition.getName(), e.getMessage());
            collectTrajectory(
                    executionId,
                    agentId,
                    userId,
                    input,
                    e.getMessage(),
                    false,
                    Duration.between(startTime, Instant.now()));
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

    private void collectTrajectory(
            String executionId,
            String agentId,
            Long userId,
            String input,
            String output,
            boolean success,
            Duration duration) {
        try {
            var trajectory =
                    new TrajectoryCollector.Trajectory(
                            executionId,
                            agentId,
                            userId,
                            input,
                            output,
                            List.of(),
                            success,
                            duration.toMillis());
            trajectoryCollector.collect(trajectory);
        } catch (Exception e) {
            log.warn("轨迹采集失败 [{}]: {}", executionId, e.getMessage());
        }
    }
}
