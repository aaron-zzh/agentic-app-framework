package com.xuejiai.aaf.framework.engine.workflow;

import java.util.Map;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.intelligent.agent.context.AgentRunContextHolder;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.type.TypeReference;

/**
 * 工作流工具——将工作流注册为 Agent 可调用的工具。
 *
 * <p>触发方式：
 *
 * <ol>
 *   <li>用户主动提示（Agent 根据对话意图决定调用）
 *   <li>Skill 匹配（SkillDefinition.triggerIntent 匹配后绑定此工具）
 *   <li>工具注册（ToolRegistry 自动发现此 Bean）
 * </ol>
 *
 * <p>执行前通过 HITL 请求用户确认，确认后启动工作流并推送节点状态。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowTool {

    private final WorkflowEngine workflowEngine;

    /**
     * 启动工作流。
     *
     * @param processKey 工作流定义 Key（BPMN 流程 ID）
     * @param description 工作流描述（向用户展示，用于确认）
     * @param variables 流程变量（JSON 格式，可为空）
     */
    @Tool(name = "list_workflows", description = "查询系统中所有可用的工作流列表。在启动工作流前，先调用此工具了解有哪些流程可以启动。")
    public String listWorkflows() {
        var definitions = workflowEngine.listDefinitions();
        if (definitions.isEmpty()) {
            return "当前没有可用的工作流。";
        }
        var sb = new StringBuilder("可用工作流列表：\n");
        for (var def : definitions) {
            sb.append(
                    String.format(
                            "- %s（key: %s，版本: %d）\n", def.name(), def.processKey(), def.version()));
        }
        return sb.toString();
    }

    @Tool(
            name = "start_workflow",
            description = "启动一个预定义的工作流流程。适用于需要多步骤审批、自动化处理或复杂业务流程的场景，如请假申请、费用报销、采购审批等。")
    public String startWorkflow(
            @ToolParam(
                            name = "process_key",
                            description = "工作流定义 Key，如 leave-approval、expense-report")
                    String processKey,
            @ToolParam(name = "description", description = "向用户展示的工作流描述，说明将要执行什么")
                    String description,
            @ToolParam(
                            name = "variables",
                            required = false,
                            description = "流程变量，JSON 格式，如 {\"days\": 3}")
                    String variables) {

        var ctx = AgentRunContextHolder.current().orElse(null);
        var userId = ctx != null ? ctx.userId() : null;

        // 解析流程变量
        var vars = new java.util.HashMap<String, Object>();
        if (userId != null) vars.put("userId", userId);
        if (variables != null && !variables.isBlank()) {
            try {
                var parsed =
                        JsonUtils.parseObject(
                                variables, new TypeReference<Map<String, Object>>() {});
                vars.putAll(parsed);
            } catch (Exception e) {
                log.warn("流程变量解析失败，忽略: {}", variables);
            }
        }

        // 权限确认已由 AafToolPermissionHook 完成（requireConfirm=true），此处直接启动
        var sessionId = ctx != null ? ctx.runId() : "system";
        var instanceId = workflowEngine.startProcess(processKey, sessionId, vars);
        log.info("工作流已启动: processKey={}, instanceId={}", processKey, instanceId);
        return String.format("工作流「%s」已成功启动（实例 ID: %s）。", description, instanceId);
    }
}
