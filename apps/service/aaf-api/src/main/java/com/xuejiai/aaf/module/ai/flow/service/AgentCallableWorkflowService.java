package com.xuejiai.aaf.module.ai.flow.service;

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
            Long workflowId,
            Map<String, Object> variables,
            TrustedScope scope,
            String agentRunId) {
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
                                                GlobalErrorCode.NOT_FOUND,
                                                "可调用的 AI Flow 不存在"));

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
        return new WorkflowStartResult(
                flow.getId(), flow.getName(), processInstanceId, businessKey);
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
