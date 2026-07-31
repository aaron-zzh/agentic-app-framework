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
        var loopTerminated = Boolean.TRUE.equals(execution.getVariable("loopTerminated"));
        var terminationReason = String.valueOf(execution.getVariable("terminationReason"));
        var finalSuccess = goalAchieved && !loopTerminated;

        execution.setVariable("finalOutput", loopOutput);
        execution.setVariable("finalSuccess", finalSuccess);

        log.info(
                "[AgentLoop] 收尾完成 processInstanceId={} finalSuccess={} goalAchieved={} loopTerminated={} terminationReason={} totalSteps={}",
                processInstanceId,
                finalSuccess,
                goalAchieved,
                loopTerminated,
                terminationReason,
                stepCount);
    }

    private int toInt(Object val) {
        if (val instanceof Number number) {
            return number.intValue();
        }
        return 0;
    }
}
