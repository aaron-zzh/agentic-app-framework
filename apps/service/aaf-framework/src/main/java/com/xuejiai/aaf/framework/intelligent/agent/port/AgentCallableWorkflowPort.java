package com.xuejiai.aaf.framework.intelligent.agent.port;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Agent 可调用工作流的业务无关边界。 */
public interface AgentCallableWorkflowPort {

    List<WorkflowSummary> list(TrustedScope scope);

    WorkflowStartResult start(
            Long workflowId, Map<String, Object> variables, TrustedScope scope, String agentRunId);

    record TrustedScope(Long userId, Long orgId, Long workspaceId) {
        public TrustedScope {
            Objects.requireNonNull(userId, "userId 不能为空");
            Objects.requireNonNull(orgId, "orgId 不能为空");
            Objects.requireNonNull(workspaceId, "workspaceId 不能为空");
        }
    }

    record WorkflowSummary(
            Long workflowId, String name, String description, boolean requireConfirm) {}

    record WorkflowStartResult(
            Long workflowId, String workflowName, String processInstanceId, String businessKey) {}
}
