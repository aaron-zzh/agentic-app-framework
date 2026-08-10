package com.xuejiai.aaf.module.ai.flow.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.framework.engine.bpmn.api.BpmnEngine;
import com.xuejiai.aaf.framework.engine.workflow.runtime.WorkflowExecutionLog;
import com.xuejiai.aaf.framework.engine.workflow.runtime.WorkflowExecutionLogger;
import com.xuejiai.aaf.module.ai.aigc.execution.api.AigcRuntimeExecution.Command;
import com.xuejiai.aaf.module.ai.aigc.execution.api.AigcRuntimeExecution.Result;
import com.xuejiai.aaf.module.ai.aigc.execution.api.AigcRuntimeExecution.Submission;
import com.xuejiai.aaf.module.ai.aigc.execution.api.AigcWorkflowExecutionPort;
import com.xuejiai.aaf.module.ai.flow.repository.AiFlowDefinitionRepository;

import lombok.RequiredArgsConstructor;

/** 将 AIGC Workflow 动作提交到已发布的 Flowable 流程。 */
@Component
@RequiredArgsConstructor
public class AigcWorkflowExecutionAdapter implements AigcWorkflowExecutionPort {

    private static final long POLL_INTERVAL_MS = 500L;

    private final AiFlowDefinitionRepository flowRepository;
    private final AiFlowBpmnCompiler bpmnCompiler;
    private final BpmnEngine bpmnEngine;
    private final WorkflowExecutionLogger executionLogger;

    @Override
    public Submission submit(Command command) {
        var flowId = parseFlowId(command.targetRef());
        var flow =
                flowRepository
                        .findById(flowId)
                        .orElseThrow(
                                () -> new BusinessException(GlobalErrorCode.NOT_FOUND, "工作流不存在"));
        var flowWorkspaceId = flow.getWorkspaceId() == null ? 0L : flow.getWorkspaceId();
        var commandWorkspaceId = command.workspaceId() == null ? 0L : command.workspaceId();
        if (!command.orgId().equals(flow.getOrgId()) || commandWorkspaceId != flowWorkspaceId) {
            throw new BusinessException(GlobalErrorCode.NOT_FOUND, "工作流不存在");
        }
        if (!"PUBLISHED".equals(flow.getStatus())) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "工作流未发布，无法运行");
        }

        var runtimeRunId = "aigc:" + command.executionRunId();
        var variables = new LinkedHashMap<String, Object>(command.input());
        variables.put("prompt", command.prompt());
        variables.put("actionKey", command.actionKey());
        variables.put("projectId", command.projectId());
        variables.put("projectObjectId", command.projectObjectId());
        variables.put("_aafOrgId", command.orgId());
        variables.put("_aafWorkspaceId", commandWorkspaceId);
        variables.put("_aafUserId", command.userId());
        var processInstanceId =
                bpmnEngine.startProcess(bpmnCompiler.processKey(flowId), runtimeRunId, variables);
        var completion = new CompletableFuture<Result>();
        Thread.startVirtualThread(() -> awaitCompletion(processInstanceId, completion));
        return new Submission(processInstanceId, runtimeRunId, completion);
    }

    private void awaitCompletion(String processInstanceId, CompletableFuture<Result> completion) {
        try {
            while (bpmnEngine.isProcessRunning(processInstanceId)) {
                Thread.sleep(POLL_INTERVAL_MS);
            }
            var instance = bpmnEngine.getInstance(processInstanceId);
            if (instance == null || !"completed".equals(instance.status())) {
                throw new IllegalStateException(
                        instance == null
                                ? "Workflow runtime 实例不存在"
                                : "Workflow runtime 非正常终止: " + instance.status());
            }
            var logs = executionLogger.getExecutionLogs(processInstanceId);
            var failure =
                    logs.stream()
                            .filter(log -> "failed".equals(log.status()))
                            .reduce((first, second) -> second);
            if (failure.isPresent()) {
                completion.completeExceptionally(
                        new IllegalStateException(
                                failure.get().error() == null
                                        ? "Workflow runtime 执行失败"
                                        : failure.get().error()));
                return;
            }
            var output =
                    logs.stream()
                            .filter(log -> "completed".equals(log.status()))
                            .map(WorkflowExecutionLog::output)
                            .filter(java.util.Objects::nonNull)
                            .reduce((first, second) -> second)
                            .orElse("");
            completion.complete(
                    new Result(
                            output,
                            Map.of(
                                    "runtimeType",
                                    "workflow",
                                    "processInstanceId",
                                    processInstanceId,
                                    "nodeCount",
                                    logs.size())));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            completion.completeExceptionally(exception);
        } catch (RuntimeException exception) {
            completion.completeExceptionally(exception);
        }
    }

    private Long parseFlowId(String targetRef) {
        try {
            return Long.valueOf(targetRef);
        } catch (NumberFormatException exception) {
            throw new BusinessException(
                    GlobalErrorCode.BAD_REQUEST, "Workflow targetRef 必须为工作流定义 ID");
        }
    }
}
