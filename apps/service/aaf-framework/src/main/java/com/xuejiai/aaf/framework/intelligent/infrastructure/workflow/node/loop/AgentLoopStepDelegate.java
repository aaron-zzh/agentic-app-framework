package com.xuejiai.aaf.framework.intelligent.infrastructure.workflow.node.loop;

import java.time.Instant;
import java.util.Map;

import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.engine.checkpoint.CheckpointEntry;
import com.xuejiai.aaf.framework.engine.checkpoint.CheckpointPolicy.CheckpointConfig;
import com.xuejiai.aaf.framework.engine.checkpoint.CheckpointStore;
import com.xuejiai.aaf.framework.intelligent.infrastructure.workflow.node.AgentNode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Agent 持久化循环步骤节点——执行一次 Agent 回合并保存循环状态与检查点。 */
@Slf4j
@Component("agentLoopStepDelegate")
@RequiredArgsConstructor
public class AgentLoopStepDelegate implements JavaDelegate {

    private final ObjectProvider<AgentNode> agentNodeProvider;
    private final CheckpointStore checkpointStore;

    @Override
    public void execute(DelegateExecution execution) {
        var processInstanceId = execution.getProcessInstanceId();
        var completedSteps = intVariable(execution, "stepCount", 0);
        var maxSteps = intVariable(execution, "maxSteps", 20);
        execution.setVariable("needsApproval", false);

        if (completedSteps >= maxSteps) {
            terminateForBudget(execution, completedSteps, maxSteps);
            return;
        }

        var stepCount = completedSteps + 1;
        execution.setVariable("stepCount", stepCount);
        var input = stringVariable(execution, "input", "");
        var previousOutput = stringVariable(execution, "loopOutput", "");
        var stepInput = previousOutput.isBlank() ? input : input + "\n\n[上一步结果]\n" + previousOutput;

        execution.setVariable("input", stepInput);
        execution.setVariable("output", "");
        execution.setVariable("success", false);
        try {
            var agentNode = agentNodeProvider.getIfAvailable();
            if (agentNode == null) {
                throw new IllegalStateException("Agent 执行能力未配置");
            }
            agentNode.execute(execution);
        } catch (RuntimeException exception) {
            execution.setVariable("input", input);
            terminateForFailure(execution, stepCount, previousOutput, exception.getMessage());
            return;
        } finally {
            execution.setVariable("input", input);
        }

        var stepResult = stringVariable(execution, "output", "");
        var success = Boolean.TRUE.equals(execution.getVariable("success"));
        var loopOutput = appendOutput(previousOutput, stepCount, stepResult);
        execution.setVariable("loopOutput", loopOutput);

        if (!success) {
            terminateForFailure(execution, stepCount, previousOutput, stepResult);
            return;
        }

        var goalAchieved = isGoalAchieved(stepResult);
        if (!goalAchieved && stepCount >= maxSteps) {
            terminateForBudget(execution, stepCount, maxSteps);
            return;
        }

        execution.setVariable("stepResult", stepResult);
        execution.setVariable("stepSuccess", true);
        execution.setVariable("goalAchieved", goalAchieved);
        execution.setVariable("loopTerminated", false);
        execution.setVariable("terminationReason", "");
        execution.setVariable("needsApproval", !goalAchieved && needsApproval(stepResult));
        saveCheckpoint(execution, stepCount, true, stepResult);

        log.info(
                "[AgentLoop] 步骤完成 processInstanceId={} step={}/{} goalAchieved={} needsApproval={}",
                processInstanceId,
                stepCount,
                maxSteps,
                goalAchieved,
                execution.getVariable("needsApproval"));
    }

    private void terminateForFailure(
            DelegateExecution execution,
            int stepCount,
            String previousOutput,
            String failureMessage) {
        var stepResult =
                failureMessage == null || failureMessage.isBlank()
                        ? "Agent 步骤执行失败"
                        : failureMessage;
        execution.setVariable("stepResult", stepResult);
        execution.setVariable("stepSuccess", false);
        execution.setVariable("goalAchieved", false);
        execution.setVariable("needsApproval", false);
        execution.setVariable("loopTerminated", true);
        execution.setVariable("terminationReason", "STEP_FAILED");
        execution.setVariable("loopOutput", appendOutput(previousOutput, stepCount, stepResult));
        saveCheckpoint(execution, stepCount, false, stepResult);
        log.warn(
                "[AgentLoop] 步骤失败 processInstanceId={} step={} reason={}",
                execution.getProcessInstanceId(),
                stepCount,
                stepResult);
    }

    private void terminateForBudget(DelegateExecution execution, int stepCount, int maxSteps) {
        var stepResult = "已达到最大步骤数 %d".formatted(maxSteps);
        execution.setVariable("stepResult", stepResult);
        execution.setVariable("stepSuccess", false);
        execution.setVariable("goalAchieved", false);
        execution.setVariable("needsApproval", false);
        execution.setVariable("loopTerminated", true);
        execution.setVariable("terminationReason", "MAX_STEPS_EXCEEDED");
        saveCheckpoint(execution, stepCount, false, stepResult);
        log.warn(
                "[AgentLoop] 步数耗尽 processInstanceId={} stepCount={} maxSteps={}",
                execution.getProcessInstanceId(),
                stepCount,
                maxSteps);
    }

    private String appendOutput(String previousOutput, int stepCount, String stepResult) {
        return previousOutput.isBlank()
                ? stepResult
                : previousOutput + "\n\n[步骤" + stepCount + "]\n" + stepResult;
    }

    private void saveCheckpoint(
            DelegateExecution execution, int stepCount, boolean success, String output) {
        var executionId = execution.getVariable("executionId");
        if (executionId == null) {
            return;
        }
        var ownerId = String.valueOf(executionId);
        var createdAt = Instant.now();
        checkpointStore.save(
                new CheckpointEntry(
                        "%s:loop_step:%d".formatted(ownerId, stepCount),
                        ownerId,
                        CheckpointEntry.OwnerType.AGENT,
                        stepCount,
                        Map.of(
                                "step",
                                stepCount,
                                "success",
                                success,
                                "output",
                                output,
                                "loopTerminated",
                                Boolean.TRUE.equals(execution.getVariable("loopTerminated")),
                                "terminationReason",
                                stringVariable(execution, "terminationReason", "")),
                        createdAt,
                        createdAt.plus(CheckpointConfig.defaults().ttl())));
    }

    private boolean isGoalAchieved(String output) {
        return output.contains("[DONE]") || output.contains("[任务完成]");
    }

    private boolean needsApproval(String output) {
        return output.contains("[需要审批]") || output.contains("[NEEDS_APPROVAL]");
    }

    private String stringVariable(DelegateExecution execution, String name, String defaultValue) {
        var value = execution.getVariable(name);
        return value == null ? defaultValue : String.valueOf(value);
    }

    private int intVariable(DelegateExecution execution, String name, int defaultValue) {
        var value = execution.getVariable(name);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return value == null ? defaultValue : Integer.parseInt(String.valueOf(value));
    }
}
