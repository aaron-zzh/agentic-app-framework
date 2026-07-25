package com.xuejiai.aaf.framework.engine.workflow.node.loop;

import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Agent Loop 收尾节点——写入流程最终输出。
 *
 * <p>读取流程变量：loopOutput, stepCount, goalAchieved
 *
 * <p>写入流程变量：finalOutput（最终输出，供后续节点或调用方读取）
 *
 * @author Kiro
 */
@Slf4j
@Component("agentLoopFinalizeDelegate")
public class AgentLoopFinalizeDelegate implements JavaDelegate {

    @Override
    public void execute(DelegateExecution execution) {
        var processInstanceId = execution.getProcessInstanceId();
        var loopOutput = (String) execution.getVariable("loopOutput");
        var stepCount = toInt(execution.getVariable("stepCount"));
        var goalAchieved = Boolean.TRUE.equals(execution.getVariable("goalAchieved"));

        execution.setVariable("finalOutput", loopOutput);

        log.info(
                "[AgentLoop] 收尾完成 processInstanceId={} goalAchieved={} totalSteps={}",
                processInstanceId,
                goalAchieved,
                stepCount);
    }

    private int toInt(Object val) {
        if (val instanceof Number number) {
            return number.intValue();
        }
        return 0;
    }
}
