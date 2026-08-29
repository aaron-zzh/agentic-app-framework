package com.xuejiai.aaf.framework.intelligent.agent.port;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Agent 可调用工作流的业务无关边界。 */
public interface AgentCallableWorkflowPort {

    List<WorkflowSummary> list(TrustedScope scope);

    /**
     * 启动已发布工作流并同步等待其完成。
     *
     * <p>超时未完成时抛出 {@link IllegalStateException}；工作流以 {@code terminated} 状态结束时同样视为失败。
     */
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

    /**
     * 工作流执行结果。
     *
     * @param outputText 结束节点约定输出变量 {@code output} 的文本值；未配置时为空字符串，代表成功但无文字产出
     */
    record WorkflowStartResult(
            Long workflowId,
            String workflowName,
            String processInstanceId,
            String businessKey,
            String outputText) {}
}
