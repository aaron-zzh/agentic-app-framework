package com.xuejiai.aaf.framework.engine.workflow.node;

import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.engine.meta.ExecutionDispatcher;
import com.xuejiai.aaf.framework.engine.meta.ExecutionDispatcher.ExecutionRequest;
import com.xuejiai.aaf.framework.engine.meta.ExecutionDispatcher.ExecutionTarget;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 工作流 Assistant 节点——通过元引擎 ExecutionDispatcher 调度 Assistant 执行。
 *
 * <p>BPMN 用法：{@code flowable:delegateExpression="${assistantNode}"}
 *
 * <p>流程变量：assistantId/userId/input（必填）、sessionId（可选）、output/success/error（节点写入）
 */
@Slf4j
@Component("assistantNode")
@RequiredArgsConstructor
public class AssistantNode implements JavaDelegate {

    private final ExecutionDispatcher dispatcher;

    @Override
    public void execute(DelegateExecution execution) {
        var assistantId = (String) execution.getVariable("assistantId");
        var userId = ((Number) execution.getVariable("userId")).longValue();
        var input = (String) execution.getVariable("input");
        var sessionId = execution.getVariable("sessionId") != null
                ? (String) execution.getVariable("sessionId")
                : execution.getProcessInstanceId();

        var request = new ExecutionRequest(
                ExecutionTarget.ASSISTANT, assistantId, input,
                sessionId, userId, null, 0.9, true);
        var result = dispatcher.dispatch(request);

        execution.setVariable("output", result.output());
        execution.setVariable("success", result.success());
        if (!result.success()) {
            execution.setVariable("error", result.error());
        }
    }
}
