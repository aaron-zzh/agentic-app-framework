package com.xuejiai.aaf.framework.engine.workflow.node.loop;

import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.engine.task.CheckpointStore;
import com.xuejiai.aaf.framework.engine.task.TaskEventBus;
import com.xuejiai.aaf.framework.intelligent.agent.AgentDefinition;
import com.xuejiai.aaf.framework.intelligent.agent.AgentRegistryService;
import com.xuejiai.aaf.framework.intelligent.agent.runtime.CognitiveCycleExecutor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Agent Loop 步骤节点——执行一次认知循环（中层 Loop 的单步）。
 *
 * <p>职责：
 *
 * <ol>
 *   <li>从流程变量读取当前输入和 Agent 定义
 *   <li>调用 {@link CognitiveCycleExecutor} 执行内层 ReAct 循环
 *   <li>更新 stepCount、loopOutput
 *   <li>评估 goalAchieved / needsApproval（Step Budget 检查）
 *   <li>写入检查点（Checkpoint）
 *   <li>发布步骤事件
 * </ol>
 *
 * <p>流程变量（读取）：agentId, input, userId, stepCount, maxSteps, loopOutput, goalCondition
 *
 * <p>流程变量（写入）：stepCount, loopOutput, goalAchieved, needsApproval, stepResult, stepSuccess
 *
 * @author Kiro
 */
@Slf4j
@Component("agentLoopStepDelegate")
@RequiredArgsConstructor
public class AgentLoopStepDelegate implements JavaDelegate {

    private final CognitiveCycleExecutor cognitiveCycleExecutor;
    private final AgentRegistryService agentRegistry;
    private final CheckpointStore checkpointStore;
    private final TaskEventBus taskEventBus;

    @Override
    public void execute(DelegateExecution execution) {
        var processInstanceId = execution.getProcessInstanceId();
        var agentId = (String) execution.getVariable("agentId");
        var input = (String) execution.getVariable("input");
        var userId = toLong(execution.getVariable("userId"));
        var stepCount = toInt(execution.getVariable("stepCount")) + 1;
        var maxSteps = toInt(execution.getVariable("maxSteps"));
        var prevOutput = (String) execution.getVariable("loopOutput");
        // 缺口1修复：读取 conversationId 和 knowledgeBaseId，传给混合检索
        var conversationId = (String) execution.getVariable("conversationId");
        var knowledgeBaseId = toLong(execution.getVariable("knowledgeBaseId"));

        execution.setVariable("stepCount", stepCount);

        // Step Budget 检查：超限直接标记完成（不报错，以最佳结果收尾）
        if (stepCount > maxSteps) {
            log.warn(
                    "[AgentLoop] 步数超限 processInstanceId={} stepCount={}",
                    processInstanceId,
                    stepCount);
            execution.setVariable("goalAchieved", true);
            execution.setVariable("stepSuccess", false);
            taskEventBus.publish(
                    toLong(execution.getVariable("taskId")),
                    null,
                    null,
                    "step_budget_exceeded",
                    "{\"stepCount\":%d,\"maxSteps\":%d}".formatted(stepCount, maxSteps));
            return;
        }

        // 构造本步输入：原始输入 + 上一步输出作为上下文
        var stepInput =
                prevOutput != null && !prevOutput.isBlank()
                        ? input + "\n\n[上一步结果]\n" + prevOutput
                        : input;

        // 发布步骤开始事件
        taskEventBus.publish(
                toLong(execution.getVariable("taskId")),
                null,
                null,
                "step_started",
                "{\"step\":%d,\"maxSteps\":%d}".formatted(stepCount, maxSteps));

        // 执行内层 Loop（ReAct 认知循环），传入 conversationId + knowledgeBaseId 接通混合检索
        var agentDef = resolveAgent(agentId);
        var result =
                cognitiveCycleExecutor.execute(
                        agentDef, stepInput, userId, conversationId, null, knowledgeBaseId);

        var stepResult = result.response();
        execution.setVariable("stepResult", stepResult);
        execution.setVariable("stepSuccess", result.success());

        // 累计输出
        var newOutput =
                prevOutput != null && !prevOutput.isBlank()
                        ? prevOutput + "\n\n[步骤" + stepCount + "]\n" + stepResult
                        : stepResult;
        execution.setVariable("loopOutput", newOutput);

        // 写检查点
        var executionId = toLong(execution.getVariable("executionId"));
        if (executionId != null) {
            checkpointStore.save(
                    executionId,
                    "loop_step",
                    stepCount,
                    "{\"step\":%d,\"success\":%b,\"output\":\"%s\"}"
                            .formatted(stepCount, result.success(), escape(stepResult)));
        }

        // 目标达成判断：Agent 明确表示完成，或执行失败
        var goalAchieved = !result.success() || isGoalAchieved(stepResult, execution);
        execution.setVariable("goalAchieved", goalAchieved);

        // 高风险操作检测 → 需要人工审批
        var needsApproval = detectNeedsApproval(stepResult);
        execution.setVariable("needsApproval", needsApproval);

        // 发布步骤完成事件
        taskEventBus.publish(
                toLong(execution.getVariable("taskId")),
                null,
                null,
                "step_completed",
                "{\"step\":%d,\"success\":%b,\"goalAchieved\":%b,\"needsApproval\":%b}"
                        .formatted(stepCount, result.success(), goalAchieved, needsApproval));

        log.info(
                "[AgentLoop] 步骤完成 processInstanceId={} step={}/{} goalAchieved={} needsApproval={}",
                processInstanceId,
                stepCount,
                maxSteps,
                goalAchieved,
                needsApproval);
    }

    /** 解析 Agent 定义——优先按 ID 查找，找不到用默认 Agent */
    private AgentDefinition resolveAgent(String agentId) {
        if (agentId != null) {
            var found =
                    agentRegistry.listActive().stream()
                            .filter(a -> agentId.equals(String.valueOf(a.getId())))
                            .findFirst();
            if (found.isPresent()) return found.get();
        }
        return agentRegistry.listActive().stream()
                .findFirst()
                .orElseGet(
                        () -> {
                            var def = new AgentDefinition();
                            def.setName("默认助理");
                            def.setSystemPrompt("你是一个有帮助的 AI 助手，请完成用户交给你的任务。");
                            def.setTimeoutSeconds(120);
                            return def;
                        });
    }

    /** 目标达成判断：检查 Agent 输出中是否包含完成标记 */
    private boolean isGoalAchieved(String output, DelegateExecution execution) {
        if (output == null) return false;
        // 检查 Agent 是否明确输出完成信号
        if (output.contains("[DONE]") || output.contains("[任务完成]")) return true;
        // 如果有自定义目标条件，可扩展此处
        return false;
    }

    /** 检测是否需要人工审批（高风险操作关键词） */
    private boolean detectNeedsApproval(String output) {
        if (output == null) return false;
        return output.contains("[需要审批]") || output.contains("[NEEDS_APPROVAL]");
    }

    private int toInt(Object val) {
        if (val instanceof Number n) return n.intValue();
        if (val instanceof String s) return Integer.parseInt(s);
        return 0;
    }

    private Long toLong(Object val) {
        if (val instanceof Number n) return n.longValue();
        if (val instanceof String s) return Long.parseLong(s);
        return null;
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
