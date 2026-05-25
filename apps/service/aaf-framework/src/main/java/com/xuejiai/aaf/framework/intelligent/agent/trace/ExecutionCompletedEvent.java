package com.xuejiai.aaf.framework.intelligent.agent.trace;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Agent 执行完成事件（成功或失败均发布）。 */
public record ExecutionCompletedEvent(
        String executionId,
        String parentExecutionId,
        String agentId,
        String agentName,
        Long userId,
        String conversationId,
        String input,
        String output,
        ExecutionStatus status,
        String errorMessage,
        int tokenInput,
        int tokenOutput,
        Instant startedAt,
        Instant finishedAt,
        int retryCount,
        List<StepRecord> steps,
        Map<String, Object> metadata) {

    /** 单个步骤记录。 */
    public record StepRecord(
            int stepIndex,
            Long parentStepId,
            StepType stepType,
            String agentId,
            String toolName,
            String input,
            String output,
            ExecutionStatus status,
            String errorMessage,
            Instant startedAt,
            Instant finishedAt) {}
}
