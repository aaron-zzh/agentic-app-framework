package com.xuejiai.aaf.framework.intelligent.agent.trace;

import java.time.Duration;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 执行追踪异步监听器——将执行结果持久化到 PostgreSQL。
 *
 * <p>通过 Spring Event 异步写入，不阻塞 Agent 执行主路径。写入失败仅记录 warn 日志。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExecutionTraceListener {

    private final ExecutionRunRepository runRepository;
    private final ExecutionStepRepository stepRepository;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMPLETION)
    public void onExecutionCompleted(ExecutionCompletedEvent event) {
        try {
            persistRun(event);
        } catch (Exception e) {
            log.warn("执行追踪写入失败 [{}]: {}", event.executionId(), e.getMessage());
        }
    }

    private void persistRun(ExecutionCompletedEvent event) {
        var run = new ExecutionRun();
        run.setExecutionId(event.executionId());
        run.setAgentId(event.agentId());
        run.setAgentName(event.agentName());
        run.setUserId(event.userId());
        run.setConversationId(event.conversationId());
        run.setInput(event.input());
        run.setOutput(event.output());
        run.setStatus(event.status());
        run.setErrorMessage(event.errorMessage());
        run.setTokenInput(event.tokenInput());
        run.setTokenOutput(event.tokenOutput());
        run.setStartedAt(event.startedAt());
        run.setFinishedAt(event.finishedAt());
        run.setRetryCount(event.retryCount());
        run.setMetadata(event.metadata());

        if (event.finishedAt() != null && event.startedAt() != null) {
            run.setDurationMs(Duration.between(event.startedAt(), event.finishedAt()).toMillis());
        }

        // 解析 parentRunId
        if (event.parentExecutionId() != null) {
            runRepository.findByExecutionId(event.parentExecutionId())
                    .ifPresent(parent -> run.setParentRunId(parent.getId()));
        }

        var savedRun = runRepository.save(run);

        // 批量写入步骤
        if (event.steps() != null) {
            for (var stepRecord : event.steps()) {
                var step = new ExecutionStep();
                step.setRunId(savedRun.getId());
                step.setParentStepId(stepRecord.parentStepId());
                step.setStepIndex(stepRecord.stepIndex());
                step.setStepType(stepRecord.stepType());
                step.setAgentId(stepRecord.agentId());
                step.setToolName(stepRecord.toolName());
                step.setInput(stepRecord.input());
                step.setOutput(stepRecord.output());
                step.setStatus(stepRecord.status());
                step.setErrorMessage(stepRecord.errorMessage());
                step.setStartedAt(stepRecord.startedAt());
                step.setFinishedAt(stepRecord.finishedAt());
                if (stepRecord.finishedAt() != null && stepRecord.startedAt() != null) {
                    step.setDurationMs(
                            Duration.between(stepRecord.startedAt(), stepRecord.finishedAt()).toMillis());
                }
                stepRepository.save(step);
            }
        }

        log.debug("执行追踪已持久化 [{}] steps={}", event.executionId(),
                event.steps() != null ? event.steps().size() : 0);
    }
}
