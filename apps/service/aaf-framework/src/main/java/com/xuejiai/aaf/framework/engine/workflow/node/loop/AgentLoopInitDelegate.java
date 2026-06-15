package com.xuejiai.aaf.framework.engine.workflow.node.loop;

import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

/**
 * Agent Loop 初始化节点——在流程启动时设置循环所需的初始状态变量。
 *
 * <p>流程变量（输入，由启动方传入）：
 *
 * <ul>
 *   <li>{@code agentId} — Agent 标识（必填）
 *   <li>{@code input} — 任务输入文本（必填）
 *   <li>{@code userId} — 用户 ID（必填）
 *   <li>{@code goalCondition} — 目标完成判断条件描述（可选）
 *   <li>{@code maxSteps} — 最大步数，默认 20（可选）
 * </ul>
 *
 * <p>流程变量（输出，本节点写入）：
 *
 * <ul>
 *   <li>{@code stepCount} — 当前步数，初始化为 0
 *   <li>{@code goalAchieved} — 目标是否达成，初始化为 false
 *   <li>{@code needsApproval} — 是否需要人工审批，初始化为 false
 *   <li>{@code loopOutput} — 累计输出，初始化为空字符串
 * </ul>
 *
 * @author Kiro
 */
@Component("agentLoopInitDelegate")
public class AgentLoopInitDelegate implements JavaDelegate {

    private static final int DEFAULT_MAX_STEPS = 20;

    @Override
    public void execute(DelegateExecution execution) {
        // 初始化循环状态变量
        execution.setVariable("stepCount", 0);
        execution.setVariable("goalAchieved", false);
        execution.setVariable("needsApproval", false);
        execution.setVariable("loopOutput", "");

        // 设置默认 maxSteps
        if (execution.getVariable("maxSteps") == null) {
            execution.setVariable("maxSteps", DEFAULT_MAX_STEPS);
        }
    }
}
