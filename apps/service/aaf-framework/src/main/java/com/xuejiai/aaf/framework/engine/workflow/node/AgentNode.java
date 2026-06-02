package com.xuejiai.aaf.framework.engine.workflow.node;

import java.util.Map;

import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.xuejiai.aaf.framework.engine.meta.ExecutionDispatcher;
import com.xuejiai.aaf.framework.engine.meta.ExecutionDispatcher.ExecutionRequest;
import com.xuejiai.aaf.framework.engine.meta.ExecutionDispatcher.ExecutionTarget;
import com.xuejiai.aaf.framework.intelligent.agent.context.AgentRunContextHolder;
import com.xuejiai.aaf.framework.intelligent.agent.trace.AgentRunEventPublisher;
import com.xuejiai.aaf.framework.intelligent.agent.trace.AgentRunEventType;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 工作流 Agent 节点——通过元引擎 ExecutionDispatcher 调度 Agent 执行。
 *
 * <p>BPMN 用法：{@code flowable:delegateExpression="${agentNode}"}
 *
 * <p>流程变量：
 *
 * <ul>
 *   <li>agentId（必填）——目标 Agent 标识
 *   <li>input（必填）——输入文本
 *   <li>promptOverride（可选）——覆盖 Agent 默认 Prompt
 *   <li>modelId（可选）——指定模型
 *   <li>temperature（可选）——温度参数
 *   <li>maxTokens（可选）——最大 Token 数
 *   <li>tools（可选）——逗号分隔工具名列表
 *   <li>inputMapping（可选）——JSON 格式输入映射
 *   <li>outputMapping（可选）——JSON 格式输出映射
 *   <li>output/success/error（节点写入）
 * </ul>
 */
@Slf4j
@Component("agentNode")
@RequiredArgsConstructor
public class AgentNode implements JavaDelegate {

    private final ExecutionDispatcher dispatcher;
    private final ObjectMapper objectMapper;
    private final AgentRunEventPublisher agentRunEventPublisher;

    @Override
    public void execute(DelegateExecution execution) {
        var agentId = (String) execution.getVariable("agentId");
        var input = resolveInput(execution);
        var nodeId = execution.getCurrentActivityId();

        // 推送节点开始事件
        var runCtx = AgentRunContextHolder.current().orElse(null);
        if (runCtx != null) {
            agentRunEventPublisher.publish(
                    runCtx,
                    AgentRunEventType.SUB_AGENT_STARTED,
                    "工作流节点执行",
                    "Agent: " + agentId,
                    Map.of("nodeId", nodeId, "agentId", agentId));
        }

        // 构建带增强参数的请求
        var promptOverride = (String) execution.getVariable("promptOverride");
        var modelId = (String) execution.getVariable("modelId");
        var tools = (String) execution.getVariable("tools");

        // 将增强参数写入流程变量供 Agent 运行时读取
        if (promptOverride != null) {
            execution.setVariable("_agent_promptOverride", promptOverride);
        }
        if (modelId != null) {
            execution.setVariable("_agent_modelId", modelId);
        }
        if (tools != null) {
            execution.setVariable("_agent_tools", tools);
        }
        var temperature = execution.getVariable("temperature");
        if (temperature != null) {
            execution.setVariable("_agent_temperature", temperature);
        }
        var maxTokens = execution.getVariable("maxTokens");
        if (maxTokens != null) {
            execution.setVariable("_agent_maxTokens", maxTokens);
        }

        var request = new ExecutionRequest(ExecutionTarget.AGENT, agentId, input);
        var result = dispatcher.dispatch(request);

        // 输出映射
        var output = result.output();
        applyOutputMapping(execution, output);

        execution.setVariable("output", output);
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

    /** 解析输入——支持 inputMapping 从流程变量组装输入。 */
    private String resolveInput(DelegateExecution execution) {
        var inputMapping = (String) execution.getVariable("inputMapping");
        if (inputMapping == null) {
            return (String) execution.getVariable("input");
        }
        try {
            Map<String, String> mapping =
                    objectMapper.readValue(inputMapping, new TypeReference<>() {});
            var sb = new StringBuilder();
            mapping.forEach(
                    (key, varName) -> {
                        var value = execution.getVariable(varName);
                        if (value != null) {
                            sb.append(key).append(": ").append(value).append("\n");
                        }
                    });
            return sb.toString().trim();
        } catch (Exception e) {
            log.warn("Agent 节点 inputMapping 解析失败，使用原始 input", e);
            return (String) execution.getVariable("input");
        }
    }

    /** 应用输出映射——将 output 按 outputMapping 写入多个流程变量。 */
    private void applyOutputMapping(DelegateExecution execution, String output) {
        var outputMapping = (String) execution.getVariable("outputMapping");
        if (outputMapping == null || output == null) {
            return;
        }
        try {
            Map<String, String> mapping =
                    objectMapper.readValue(outputMapping, new TypeReference<>() {});
            // 尝试将 output 解析为 JSON 对象
            Map<String, Object> outputObj =
                    objectMapper.readValue(output, new TypeReference<>() {});
            mapping.forEach(
                    (outputKey, varName) -> {
                        var value = outputObj.get(outputKey);
                        if (value != null) {
                            execution.setVariable(varName, value.toString());
                        }
                    });
        } catch (Exception e) {
            log.warn("Agent 节点 outputMapping 解析失败，跳过映射", e);
        }
    }
}
