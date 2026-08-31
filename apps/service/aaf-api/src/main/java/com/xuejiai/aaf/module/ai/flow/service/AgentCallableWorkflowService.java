package com.xuejiai.aaf.module.ai.flow.service;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.framework.engine.bpmn.api.BpmnEngine;
import com.xuejiai.aaf.framework.intelligent.agent.port.AgentCallableWorkflowPort;
import com.xuejiai.aaf.module.ai.flow.domain.AiFlowDefinition;
import com.xuejiai.aaf.module.ai.flow.repository.AiFlowDefinitionRepository;

import lombok.RequiredArgsConstructor;

/** Agent 可调用 AI Flow 的业务适配器。 */
@Service
@RequiredArgsConstructor
public class AgentCallableWorkflowService implements AgentCallableWorkflowPort {

    private static final String PUBLISHED = "PUBLISHED";
    private static final String USER_ID_VARIABLE = "_aafUserId";
    private static final String ORG_ID_VARIABLE = "_aafOrgId";
    private static final String WORKSPACE_ID_VARIABLE = "_aafWorkspaceId";
    private static final String OUTPUT_VARIABLE = "output";
    private static final Duration COMPLETION_TIMEOUT = Duration.ofMinutes(5);
    private static final Duration POLL_INTERVAL = Duration.ofMillis(200);

    private final AiFlowDefinitionRepository repository;
    private final AiFlowBpmnCompiler bpmnCompiler;
    private final BpmnEngine bpmnEngine;

    @Override
    public List<WorkflowSummary> list(TrustedScope scope) {
        return repository.findAll(callableScope(scope)).stream()
                .map(
                        flow ->
                                new WorkflowSummary(
                                        flow.getId(),
                                        flow.getName(),
                                        flow.getDescription(),
                                        Boolean.TRUE.equals(flow.getRequireConfirm())))
                .toList();
    }

    @Override
    public WorkflowStartResult start(
            Long workflowId, Map<String, Object> variables, TrustedScope scope, String agentRunId) {
        if (workflowId == null || workflowId <= 0) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "workflowId 必须为正整数");
        }
        if (agentRunId == null || agentRunId.isBlank()) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "Agent runId 不能为空");
        }

        var flow =
                repository
                        .findOne(
                                callableScope(scope)
                                        .and(
                                                (root, query, cb) ->
                                                        cb.equal(root.get("id"), workflowId)))
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                GlobalErrorCode.NOT_FOUND, "可调用的 AI Flow 不存在"));

        var processVariables = new HashMap<String, Object>();
        if (variables != null) {
            processVariables.putAll(variables);
        }
        processVariables.put(USER_ID_VARIABLE, scope.userId());
        processVariables.put(ORG_ID_VARIABLE, scope.orgId());
        processVariables.put(WORKSPACE_ID_VARIABLE, scope.workspaceId());

        var businessKey = "agent-run:%s:%s".formatted(agentRunId, UUID.randomUUID());
        var processInstanceId =
                bpmnEngine.startProcess(
                        bpmnCompiler.processKey(flow.getId()), businessKey, processVariables);
        var outputText = awaitCompletion(processInstanceId);
        return new WorkflowStartResult(
                flow.getId(), flow.getName(), processInstanceId, businessKey, outputText);
    }

    /**
     * 轮询等待流程实例结束；超时或以 {@code terminated} 状态结束时视为失败。
     *
     * <p>不引入挂起-恢复机制：工作流执行通常是秒级到分钟级，同步阻塞加超时更符合助理单次工具调用的语义， 与 {@code AgentNode} 阻塞取结果的风格一致。
     */
    private String awaitCompletion(String processInstanceId) {
        var deadline = Instant.now().plus(COMPLETION_TIMEOUT);
        while (bpmnEngine.isProcessRunning(processInstanceId)) {
            if (Instant.now().isAfter(deadline)) {
                throw new IllegalStateException("AI Flow 执行超时: " + processInstanceId);
            }
            sleep();
        }
        var instance = bpmnEngine.getInstance(processInstanceId);
        if (instance == null) {
            throw new IllegalStateException("AI Flow 执行结果不可查: " + processInstanceId);
        }
        if (!"completed".equals(instance.status())) {
            throw new IllegalStateException(
                    "AI Flow 执行未成功完成: " + processInstanceId + ", status=" + instance.status());
        }
        var value = bpmnEngine.getProcessVariables(processInstanceId).get(OUTPUT_VARIABLE);
        return value == null ? "" : String.valueOf(value);
    }

    private void sleep() {
        try {
            Thread.sleep(POLL_INTERVAL.toMillis());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待 AI Flow 完成时被中断", interrupted);
        }
    }

    private Specification<AiFlowDefinition> callableScope(TrustedScope scope) {
        return (root, query, cb) ->
                cb.and(
                        cb.equal(root.get("orgId"), scope.orgId()),
                        cb.equal(root.get("workspaceId"), scope.workspaceId()),
                        cb.equal(root.get("status"), PUBLISHED),
                        cb.isTrue(root.get("agentCallable")),
                        cb.isFalse(root.get("deleted")),
                        cb.isNotNull(root.get("deploymentId")),
                        cb.notEqual(cb.trim(root.<String>get("deploymentId")), ""));
    }
}
