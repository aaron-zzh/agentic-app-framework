package com.xuejiai.aaf.framework.engine.workflow.loop;

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.xuejiai.aaf.framework.engine.bpmn.api.BpmnEngine;
import com.xuejiai.aaf.framework.org.OrgContext;
import com.xuejiai.aaf.framework.security.OperatorContext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * AI Agent 持久化循环启动器——启动 {@code durable-agent-loop} 流程实例。
 *
 * <p><b>流程定义加载</b>：{@code durable-agent-loop.bpmn20.xml} 位于 {@code
 * aaf-framework/src/main/resources/processes/}，Flowable Spring Boot 自动扫描 {@code
 * classpath:/processes/} 下所有 {@code *.bpmn20.xml} 并在应用 启动时部署，无需手动调用 deploy()。
 *
 * <p><b>三层 Loop 分工</b>：
 *
 * <ul>
 *   <li>外层 Loop（任务级）：调用方（Assistant / AgentTaskRuntime）负责，传入 input/goalCondition
 *   <li>中层 Loop（步骤级）：本流程 + Flowable，负责持久化、检查点、人工节点
 *   <li>内层 Loop（ReAct）：AgentExecutionPort 统一执行入口
 * </ul>
 *
 * <p>委托任务由 AgentTaskRuntime 统一排队调度；本类只负责基于 Flowable BPMN 的步骤级持久循环。
 *
 * <p>用法示例：
 *
 * <pre>
 * var result = starter.start(DurableAgentLoopStarter.LoopRequest.of("agentId", "分析这份代码", userId));
 * // result.processInstanceId() 可用于后续查询进度、完成 Human Gate
 * </pre>
 *
 * @author Kiro
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DurableAgentLoopStarter {

    private static final String PROCESS_KEY = "durable-agent-loop";
    private static final String HUMAN_GATE_TASK_KEY = "humanApproval";
    private static final String ORG_ID_VARIABLE = "_aafOrgId";
    private static final String WORKSPACE_ID_VARIABLE = "_aafWorkspaceId";
    private static final String USER_ID_VARIABLE = "_aafUserId";
    private static final long ORGANIZATION_SCOPE = 0L;

    private final BpmnEngine bpmnEngine;
    private final OperatorContext operatorContext;

    /**
     * 启动 Agent 持久化循环。
     *
     * @param request 循环请求参数
     * @return 启动结果（含 processInstanceId）
     */
    public LoopResult start(LoopRequest request) {
        var variables = new HashMap<String, Object>();
        variables.put("agentId", request.agentId());
        variables.put("input", request.input());
        variables.put("userId", request.userId());
        if (request.maxSteps() > 0) variables.put("maxSteps", request.maxSteps());
        if (request.goalCondition() != null)
            variables.put("goalCondition", request.goalCondition());
        if (request.taskId() != null) variables.put("taskId", request.taskId());
        if (request.executionId() != null) variables.put("executionId", request.executionId());
        if (request.conversationId() != null)
            variables.put("conversationId", request.conversationId());
        if (request.knowledgeBaseId() != null)
            variables.put("knowledgeBaseId", request.knowledgeBaseId());
        if (request.extraVariables() != null) variables.putAll(request.extraVariables());

        var orgId = OrgContext.getCurrentOrgId();
        if (orgId == null) {
            throw new IllegalStateException("Agent 持久化循环必须指定组织上下文");
        }
        variables.put(ORG_ID_VARIABLE, orgId);
        variables.put(
                WORKSPACE_ID_VARIABLE,
                OrgContext.getCurrentWorkspaceId() != null
                        ? OrgContext.getCurrentWorkspaceId()
                        : ORGANIZATION_SCOPE);
        variables.put(USER_ID_VARIABLE, request.userId().toString());

        var businessKey =
                request.taskId() != null
                        ? "task:" + request.taskId()
                        : "agent:" + request.agentId() + ":" + System.currentTimeMillis();

        var processInstanceId = bpmnEngine.startProcess(PROCESS_KEY, businessKey, variables);

        log.info(
                "[DurableAgentLoop] 启动 processInstanceId={} agentId={} taskId={}",
                processInstanceId,
                request.agentId(),
                request.taskId());

        return new LoopResult(processInstanceId, businessKey);
    }

    /**
     * 查询循环当前状态。
     *
     * @param processInstanceId 流程实例 ID
     * @return 当前待办任务（null 表示已结束或无人工节点）
     */
    public BpmnEngine.TaskInfo currentTask(String processInstanceId) {
        return bpmnEngine.getCurrentTask(processInstanceId);
    }

    /**
     * 完成 Human Gate。
     *
     * @param taskId Flowable 任务 ID
     * @param accepted 是否接受继续执行
     * @param comment 操作意见
     */
    public void completeHumanGate(String taskId, boolean accepted, String comment) {
        if (taskId == null || taskId.isBlank()) {
            throw new IllegalArgumentException("taskId 不能为空");
        }
        var task = bpmnEngine.getTask(taskId);
        if (task == null) {
            throw new IllegalArgumentException("Human Gate 任务不存在");
        }
        if (!bpmnEngine.isTaskAt(taskId, PROCESS_KEY, HUMAN_GATE_TASK_KEY)) {
            throw new SecurityException("任务不是 Agent 持久化循环的 Human Gate");
        }
        var processVariables = bpmnEngine.getProcessVariables(task.processInstanceId());
        requireCurrentScope(processVariables);
        if (!operatorContext.isAuthenticated()) {
            throw new SecurityException("完成 Human Gate 前必须登录");
        }
        var operatorId =
                operatorContext
                        .currentOperatorId()
                        .orElseThrow(() -> new SecurityException("缺少当前操作人身份"))
                        .toString();
        if (!bpmnEngine.canOperateTask(taskId, operatorId)) {
            throw new SecurityException("当前操作人无权完成 Human Gate");
        }

        var variables = new HashMap<String, Object>();
        variables.put("needsApproval", false);
        variables.put("accepted", accepted);
        variables.put("approved", accepted);
        if (accepted) {
            variables.put("loopTerminated", false);
            variables.put("terminationReason", "");
        } else {
            variables.put("loopTerminated", true);
            variables.put("terminationReason", "HUMAN_REJECTED");
            variables.put("goalAchieved", false);
        }
        bpmnEngine.completeTask(taskId, variables, comment);
    }

    private void requireCurrentScope(Map<String, Object> processVariables) {
        var currentOrgId = OrgContext.getCurrentOrgId();
        if (currentOrgId == null) {
            throw new SecurityException("缺少组织上下文");
        }
        var currentWorkspaceId =
                OrgContext.getCurrentWorkspaceId() != null
                        ? OrgContext.getCurrentWorkspaceId()
                        : ORGANIZATION_SCOPE;
        var processOrgId = longVariable(processVariables, ORG_ID_VARIABLE);
        var processWorkspaceId = longVariable(processVariables, WORKSPACE_ID_VARIABLE);
        if (currentOrgId.longValue() != processOrgId
                || currentWorkspaceId != processWorkspaceId) {
            throw new SecurityException("Human Gate 任务不属于当前组织或工作区");
        }
    }

    private long longVariable(Map<String, Object> variables, String name) {
        var value = variables.get(name);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException exception) {
                throw new SecurityException("流程租户变量非法: " + name, exception);
            }
        }
        throw new SecurityException("流程缺少租户变量: " + name);
    }

    /**
     * 查询循环最终输出（流程结束后调用）。
     *
     * @param processInstanceId 流程实例 ID
     * @return finalOutput 变量值
     */
    public String getFinalOutput(String processInstanceId) {
        var vars = bpmnEngine.getProcessVariables(processInstanceId);
        return (String) vars.get("finalOutput");
    }

    // ==================== 请求/结果 Record ====================

    /**
     * Agent 循环请求参数。
     *
     * @param agentId Agent 标识（必填）
     * @param input 任务输入（必填）
     * @param userId 用户 ID（必填）
     * @param maxSteps 最大步数，0 = 使用默认值 20
     * @param goalCondition 目标完成条件描述（可选）
     * @param taskId 业务任务 ID（可选，用于事件关联）
     * @param executionId 执行实例 ID（可选，用于检查点关联）
     * @param extraVariables 额外流程变量（可选）
     */
    public record LoopRequest(
            String agentId,
            String input,
            Long userId,
            int maxSteps,
            String goalCondition,
            Long taskId,
            Long executionId,
            String conversationId,
            Long knowledgeBaseId,
            Map<String, Object> extraVariables) {

        /** 最简构造器 */
        public static LoopRequest of(String agentId, String input, Long userId) {
            return new LoopRequest(agentId, input, userId, 0, null, null, null, null, null, null);
        }
    }

    /**
     * Agent 循环启动结果。
     *
     * @param processInstanceId Flowable 流程实例 ID（用于查询进度）
     * @param businessKey 业务关联 key
     */
    public record LoopResult(String processInstanceId, String businessKey) {}
}
