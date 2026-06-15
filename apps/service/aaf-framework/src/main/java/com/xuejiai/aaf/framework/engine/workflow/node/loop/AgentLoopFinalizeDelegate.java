package com.xuejiai.aaf.framework.engine.workflow.node.loop;

import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.engine.task.CheckpointStore;
import com.xuejiai.aaf.framework.engine.task.TaskEventBus;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Agent Loop 收尾节点——持久化最终结果，发布完成事件。
 *
 * <p>读取流程变量：loopOutput, stepCount, goalAchieved, taskId, executionId
 *
 * <p>写入流程变量：finalOutput（最终输出，供后续节点或调用方读取）
 *
 * @author Kiro
 */
@Slf4j
@Component("agentLoopFinalizeDelegate")
@RequiredArgsConstructor
public class AgentLoopFinalizeDelegate implements JavaDelegate {

    private final CheckpointStore checkpointStore;
    private final TaskEventBus taskEventBus;

    @Override
    public void execute(DelegateExecution execution) {
        var processInstanceId = execution.getProcessInstanceId();
        var loopOutput = (String) execution.getVariable("loopOutput");
        var stepCount = toInt(execution.getVariable("stepCount"));
        var goalAchieved = Boolean.TRUE.equals(execution.getVariable("goalAchieved"));
        var taskId = toLong(execution.getVariable("taskId"));
        var executionId = toLong(execution.getVariable("executionId"));

        // 写入最终输出变量
        execution.setVariable("finalOutput", loopOutput);

        // 保存最终检查点
        if (executionId != null) {
            checkpointStore.save(
                    executionId,
                    "finalize",
                    stepCount,
                    "{\"goalAchieved\":%b,\"totalSteps\":%d}".formatted(goalAchieved, stepCount));
        }

        // 发布完成事件
        taskEventBus.publish(
                taskId,
                executionId,
                null,
                goalAchieved ? "task_completed" : "task_failed",
                "{\"goalAchieved\":%b,\"totalSteps\":%d}".formatted(goalAchieved, stepCount));

        log.info(
                "[AgentLoop] 收尾完成 processInstanceId={} goalAchieved={} totalSteps={}",
                processInstanceId,
                goalAchieved,
                stepCount);
    }

    private int toInt(Object val) {
        if (val instanceof Number n) return n.intValue();
        return 0;
    }

    private Long toLong(Object val) {
        if (val instanceof Number n) return n.longValue();
        return null;
    }
}
