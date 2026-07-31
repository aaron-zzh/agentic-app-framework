package com.xuejiai.aaf.framework.intelligent.infrastructure.workflow.tool;

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.intelligent.agent.context.AgentRunContextHolder;
import com.xuejiai.aaf.framework.intelligent.agent.port.AgentCallableWorkflowPort;
import com.xuejiai.aaf.framework.intelligent.agent.port.AgentCallableWorkflowPort.TrustedScope;
import com.xuejiai.aaf.framework.org.OrgContext;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.type.TypeReference;

/** 工作流工具——仅暴露当前租户允许 Agent 调用的 AI Flow。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowTool {

    private static final String RESERVED_VARIABLE_PREFIX = "_aaf";

    private final AgentCallableWorkflowPort workflowPort;

    @Tool(name = "list_workflows", description = "查询当前组织和工作区内允许智能体调用的已发布 AI Flow。")
    public String listWorkflows() {
        var invocation = requireInvocation();
        var workflows = workflowPort.list(invocation.scope());
        if (workflows.isEmpty()) {
            return "当前没有可调用的 AI Flow。";
        }
        var result = new StringBuilder("可调用 AI Flow 列表：\n");
        for (var workflow : workflows) {
            result.append("- %s（workflow_id: %d）".formatted(workflow.name(), workflow.workflowId()));
            if (workflow.description() != null && !workflow.description().isBlank()) {
                result.append("：").append(workflow.description());
            }
            result.append('\n');
        }
        return result.toString();
    }

    @Tool(name = "start_workflow", description = "按 workflow_id 启动当前租户允许智能体调用的已发布 AI Flow。")
    public String startWorkflow(
            @ToolParam(name = "workflow_id", description = "AI Flow 业务 ID") Long workflowId,
            @ToolParam(
                            name = "variables",
                            required = false,
                            description = "流程变量 JSON，可为空；变量名不能以 _aaf 开头")
                    String variables) {
        var invocation = requireInvocation();
        var parsedVariables = parseVariables(variables);
        var started =
                workflowPort.start(
                        workflowId,
                        parsedVariables,
                        invocation.scope(),
                        invocation.agentRunId());
        log.info(
                "AI Flow 已启动: workflowId={}, instanceId={}",
                started.workflowId(),
                started.processInstanceId());
        return "AI Flow「%s」已成功启动（实例 ID: %s）。"
                .formatted(started.workflowName(), started.processInstanceId());
    }

    private Map<String, Object> parseVariables(String variables) {
        if (variables == null || variables.isBlank()) {
            return Map.of();
        }
        final Map<String, Object> parsed;
        try {
            parsed =
                    JsonUtils.parseObject(
                            variables, new TypeReference<Map<String, Object>>() {});
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("variables 必须是合法 JSON 对象", exception);
        }
        if (parsed == null) {
            throw new IllegalArgumentException("variables 必须是合法 JSON 对象");
        }
        var result = new HashMap<String, Object>();
        result.putAll(parsed);
        var reservedKey =
                result.keySet().stream()
                        .filter(key -> key.startsWith(RESERVED_VARIABLE_PREFIX))
                        .findFirst();
        if (reservedKey.isPresent()) {
            throw new IllegalArgumentException("variables 包含保留变量: " + reservedKey.get());
        }
        return result;
    }

    private CurrentInvocation requireInvocation() {
        var context =
                AgentRunContextHolder.current()
                        .orElseThrow(() -> new IllegalStateException("缺少 Agent 运行上下文"));
        if (context.userId() == null) {
            throw new IllegalStateException("缺少 Agent 用户身份");
        }
        if (context.runId() == null || context.runId().isBlank()) {
            throw new IllegalStateException("缺少 Agent runId");
        }
        var orgId = OrgContext.getCurrentOrgId();
        var workspaceId = OrgContext.getCurrentWorkspaceId();
        if (orgId == null || workspaceId == null) {
            throw new IllegalStateException("缺少组织或工作区上下文");
        }
        return new CurrentInvocation(
                new TrustedScope(context.userId(), orgId, workspaceId), context.runId());
    }

    private record CurrentInvocation(TrustedScope scope, String agentRunId) {}
}
