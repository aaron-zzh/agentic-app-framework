package com.xuejiai.aaf.framework.engine.workflow.loop;

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.xuejiai.aaf.framework.engine.workflow.WorkflowEngine;

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
 *   <li>外层 Loop（任务级）：调用方（Assistant / ChatTaskScheduler）负责，传入 input/goalCondition
 *   <li>中层 Loop（步骤级）：本流程 + Flowable，负责持久化、检查点、人工节点
 *   <li>内层 Loop（ReAct）：{@link
 *       com.xuejiai.aaf.framework.intelligent.agent.runtime.CognitiveCycleExecutor}
 * </ul>
 *
 * <p>与 {@code DurableTaskExecutor} 的关系：
 *
 * <ul>
 *   <li>DurableTaskExecutor：手写状态机，v0.1 遗留，继续服务现有 ChatTask
 *   <li>DurableAgentLoopStarter：基于 Flowable BPMN，新长任务推荐使用此入口
 * </ul>
 *
 * <p>用法示例：
 *
 * <pre>
 * var result = starter.start(DurableAgentLoopStarter.LoopRequest.of("agentId", "分析这份代码", userId));
 * // result.processInstanceId() 可用于后续查询进度、完成审批
 * </pre>
 *
 * @author Kiro
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DurableAgentLoopStarter {

    private static final String PROCESS_KEY = "durable-agent-loop";

    private final WorkflowEngine workflowEngine;

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
        if (request.conversationId() != null) variables.put("conversationId", request.conversationId());
        if (request.knowledgeBaseId() != null) variables.put("knowledgeBaseId", request.knowledgeBaseId());
        if (request.extraVariables() != null) variables.putAll(request.extraVariables());

        var businessKey =
                request.taskId() != null
                        ? "task:" + request.taskId()
                        : "agent:" + request.agentId() + ":" + System.currentTimeMillis();

        var processInstanceId = workflowEngine.startProcess(PROCESS_KEY, businessKey, variables);

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
    public WorkflowEngine.TaskInfo currentTask(String processInstanceId) {
        return workflowEngine.getCurrentTask(processInstanceId);
    }

    /**
     * 完成人工审批节点，让循环继续。
     *
     * @param taskId Flowable 任务 ID
     * @param approved 是否批准
     * @param comment 审批意见
     */
    public void completeApproval(String taskId, boolean approved, String comment) {
        workflowEngine.completeTask(taskId, Map.of("approved", approved), comment);
    }

    /**
     * 查询循环最终输出（流程结束后调用）。
     *
     * @param processInstanceId 流程实例 ID
     * @return finalOutput 变量值
     */
    public String getFinalOutput(String processInstanceId) {
        var vars = workflowEngine.getProcessVariables(processInstanceId);
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
