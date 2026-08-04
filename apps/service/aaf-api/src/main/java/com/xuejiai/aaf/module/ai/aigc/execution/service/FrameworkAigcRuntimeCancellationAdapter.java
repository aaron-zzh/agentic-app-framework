package com.xuejiai.aaf.module.ai.aigc.execution.service;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.engine.bpmn.api.BpmnEngine;
import com.xuejiai.aaf.framework.intelligent.agent.port.AgentExecutionPort;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ExecutionId;
import com.xuejiai.aaf.module.ai.aigc.execution.api.AigcRuntimeCancellationPort;

import lombok.RequiredArgsConstructor;

/** 将 AIGC 取消命令传递到真实 Agent/Workflow runtime。 */
@Component
@RequiredArgsConstructor
public class FrameworkAigcRuntimeCancellationAdapter implements AigcRuntimeCancellationPort {

    private final AgentExecutionPort agentExecutionPort;
    private final BpmnEngine bpmnEngine;

    @Override
    public void cancel(String targetType, String runtimeTraceId, String reason) {
        switch (targetType) {
            case "agent" -> agentExecutionPort.cancel(new ExecutionId(runtimeTraceId)).block();
            case "workflow" -> {
                if (bpmnEngine.isProcessRunning(runtimeTraceId)) {
                    bpmnEngine.terminateInstance(runtimeTraceId, reason);
                }
            }
            default -> throw new IllegalArgumentException("不支持取消的 runtime 类型: " + targetType);
        }
    }
}
