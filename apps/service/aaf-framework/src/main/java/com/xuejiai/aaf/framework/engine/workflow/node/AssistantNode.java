package com.xuejiai.aaf.framework.engine.workflow.node;

import java.util.Map;

import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.engine.meta.ExecutionDispatcher;
import com.xuejiai.aaf.framework.engine.meta.ExecutionDispatcher.ExecutionRequest;
import com.xuejiai.aaf.framework.engine.meta.ExecutionDispatcher.ExecutionTarget;
import com.xuejiai.aaf.framework.intelligent.agent.run.AgentRunContextHolder;
import com.xuejiai.aaf.framework.intelligent.agent.run.AgentRunEventPublisher;
import com.xuejiai.aaf.framework.intelligent.agent.run.AgentRunEventType;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 工作流 Assistant 节点——通过元引擎 ExecutionDispatcher 调度 Assistant 执行。
 *
 * <p>BPMN 用法：{@code flowable:delegateExpression="${assistantNode}"}
 *
 * <p>流程变量：assistantId/userId/input（必填）、sessionId（可选）、output/success/error（节点写入）
 *
 * <p>若节点在对话上下文中执行（AgentRunContextHolder 有 runId）， 节点开始/结束状态会通过 AgentRunEventPublisher 推送到对话 SSE 流。
 */
@Slf4j
@Component("assistantNode")
@RequiredArgsConstructor
public class AssistantNode implements JavaDelegate {

    private final ExecutionDispatcher dispatcher;
    private final AgentRunEventPublisher agentRunEventPublisher;

    @Override
    public void execute(DelegateExecution execution) {
        var assistantId = (String) execution.getVariable("assistantId");
        var userId = ((Number) execution.getVariable("userId")).longValue();
        var input = (String) execution.getVariable("input");
        var sessionId =
                execution.getVariable("sessionId") != null
                        ? (String) execution.getVariable("sessionId")
                        : execution.getProcessInstanceId();
        var nodeId = execution.getCurrentActivityId();

        // 推送节点开始事件（若在对话上下文中）
        var runCtx = AgentRunContextHolder.current().orElse(null);
        if (runCtx != null) {
            agentRunEventPublisher.publish(
                    runCtx,
                    AgentRunEventType.SUB_AGENT_STARTED,
                    "工作流节点执行",
                    "Assistant: " + assistantId,
                    Map.of("nodeId", nodeId, "assistantId", assistantId));
        }

        var request =
                new ExecutionRequest(
                        ExecutionTarget.ASSISTANT,
                        assistantId,
                        input,
                        sessionId,
                        userId,
                        null,
                        0.9,
                        true);
        var result = dispatcher.dispatch(request);

        execution.setVariable("output", result.output());
        execution.setVariable("success", result.success());
        if (!result.success()) {
            execution.setVariable("error", result.error());
        }

        // 推送节点完成事件
        if (runCtx != null) {
            agentRunEventPublisher.publish(
                    runCtx,
                    AgentRunEventType.SUB_AGENT_COMPLETED,
                    "工作流节点完成",
                    result.success() ? "成功" : "失败",
                    Map.of("nodeId", nodeId, "success", result.success()));
        }
    }
}
