package com.xuejiai.aaf.framework.intelligent.agent.trace;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.xuejiai.aaf.framework.intelligent.agent.AgentCheckpointService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 执行恢复服务——Redis 优先（热恢复），PG 兜底（冷恢复）。
 *
 * <p>用于 Agent 执行失败后的断点续跑。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExecutionRecoveryService {

    private final AgentCheckpointService checkpointService;
    private final ExecutionRunRepository runRepository;
    private final ExecutionStepRepository stepRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /** 可恢复的执行状态。 */
    public record RecoverableState(
            String executionId,
            int resumeFromStep,
            List<String> completedSteps,
            Map<String, Object> intermediateResults) {}

    /**
     * 尝试恢复执行状态。Redis 优先，PG 兜底。
     *
     * @param executionId 执行 ID
     * @return 可恢复状态，empty 表示需全新执行
     */
    public Optional<RecoverableState> recover(String executionId) {
        // 1. Redis 热恢复
        var checkpoint = checkpointService.restoreCheckpoint(executionId);
        if (checkpoint != null) {
            log.debug("从 Redis 恢复执行状态 [{}] step={}", executionId, checkpoint.getCurrentStep());
            return Optional.of(
                    new RecoverableState(
                            executionId,
                            checkpoint.getCurrentStep(),
                            checkpoint.getCompletedSteps(),
                            checkpoint.getIntermediateResults()));
        }

        // 2. PG 冷恢复
        var runOpt = runRepository.findByExecutionId(executionId);
        if (runOpt.isEmpty() || runOpt.get().getStatus() == ExecutionStatus.SUCCESS) {
            return Optional.empty();
        }

        var run = runOpt.get();
        var steps = stepRepository.findByRunIdOrderByStepIndex(run.getId());
        var completedStepNames =
                steps.stream()
                        .filter(s -> s.getStatus() == ExecutionStatus.SUCCESS)
                        .map(s -> s.getStepType().name())
                        .toList();

        // 从最后一个成功步骤之后继续
        int resumeFrom =
                (int) steps.stream().filter(s -> s.getStatus() == ExecutionStatus.SUCCESS).count();

        log.debug("从 PG 冷恢复执行状态 [{}] resumeFrom={}", executionId, resumeFrom);
        return Optional.of(
                new RecoverableState(executionId, resumeFrom, completedStepNames, Map.of()));
    }
}
