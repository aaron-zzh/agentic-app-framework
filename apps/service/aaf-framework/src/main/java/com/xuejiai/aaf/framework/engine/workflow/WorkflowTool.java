package com.xuejiai.aaf.framework.engine.workflow;

import java.util.Map;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.intelligent.agent.run.AgentRunContextHolder;
import com.xuejiai.aaf.framework.intelligent.agent.run.AgentRunEventPublisher;
import com.xuejiai.aaf.framework.intelligent.agent.run.AgentRunEventType;
import com.xuejiai.aaf.framework.intelligent.assistant.HumanApprovalService;
import com.xuejiai.aaf.framework.intelligent.assistant.HumanApprovalService.ApprovalType;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 工作流工具——将工作流注册为 Agent 可调用的工具。
 *
 * <p>触发方式：
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
    private final HumanApprovalService approvalService;
    private final AgentRunEventPublisher agentRunEventPublisher;

    /**
     * 启动工作流。
     *
     * @param processKey 工作流定义 Key（BPMN 流程 ID）
     * @param description 工作流描述（向用户展示，用于确认）
     * @param variables 流程变量（JSON 格式，可为空）
     */
    @Tool(name = "start_workflow",
          description = "启动一个预定义的工作流流程。适用于需要多步骤审批、自动化处理或复杂业务流程的场景，如请假申请、费用报销、采购审批等。")
    public String startWorkflow(
            @ToolParam(name = "process_key", description = "工作流定义 Key，如 leave-approval、expense-report") String processKey,
            @ToolParam(name = "description", description = "向用户展示的工作流描述，说明将要执行什么") String description,
            @ToolParam(name = "variables", required = false, description = "流程变量，JSON 格式，如 {\"days\": 3}") String variables) {

        var ctx = AgentRunContextHolder.current().orElse(null);
        var sessionId = ctx != null ? ctx.runId() : "unknown";
        var userId = ctx != null ? ctx.userId() : null;

        // HITL：请求用户确认，非阻塞返回
        var requestId = approvalService.request(
                sessionId, userId,
                ApprovalType.ACTION_CONFIRM,
                "启动工作流确认",
                "AI 助理请求启动工作流：" + description,
                Map.of(
                        "processKey", processKey,
                        "description", description,
                        "riskLevel", "MEDIUM",
                        "grantScope", "ONCE"));

        // 推送等待确认事件
        if (ctx != null) {
            agentRunEventPublisher.publish(ctx,
                    AgentRunEventType.COORDINATION_STARTED,
                    "等待用户确认",
                    "工作流启动需要用户确认：" + description,
                    Map.of("requestId", requestId, "processKey", processKey));
        }

        // 非阻塞：返回审批请求 ID，用户确认后由 WorkflowApprovalListener 自动启动工作流
        return String.format(
                "已向您发送工作流启动确认请求（ID: %s）。请在通知中确认或拒绝启动「%s」工作流。",
                requestId, description);
    }
}
