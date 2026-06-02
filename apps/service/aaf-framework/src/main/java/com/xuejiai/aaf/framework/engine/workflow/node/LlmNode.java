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
 * 工作流 LLM 节点——通过元引擎 ExecutionDispatcher 调度 LLM 推理。
 *
 * <p>BPMN 用法：{@code flowable:delegateExpression="${llmNode}"}
 *
 * <p>流程变量：
 *
 * <ul>
 *   <li>input（必填）——用户输入
 *   <li>systemPrompt（可选）——系统提示词
 *   <li>scene（可选，默认 CHAT）——场景标识
 *   <li>userId（可选）——用户 ID
 *   <li>modelId（可选）——指定模型标识
 *   <li>temperature（可选）——温度参数
 *   <li>maxTokens（可选）——最大 Token 数
 *   <li>outputSchema（可选）——JSON Schema 字符串，启用结构化输出
 *   <li>output/success（节点写入）
 * </ul>
 */
@Slf4j
@Component("llmNode")
@RequiredArgsConstructor
public class LlmNode implements JavaDelegate {

    private final ExecutionDispatcher dispatcher;

    @Override
    public void execute(DelegateExecution execution) {
        var input = (String) execution.getVariable("input");
        var systemPrompt = (String) execution.getVariable("systemPrompt");
        var scene =
                execution.getVariable("scene") != null
                        ? (String) execution.getVariable("scene")
                        : "CHAT";
        var userId =
                execution.getVariable("userId") != null
                        ? ((Number) execution.getVariable("userId")).longValue()
                        : null;
        var modelId = (String) execution.getVariable("modelId");
        var outputSchema = (String) execution.getVariable("outputSchema");

        // 温度参数
        var temperature =
                execution.getVariable("temperature") != null
                        ? ((Number) execution.getVariable("temperature")).doubleValue()
                        : 0.9;

        // 结构化输出：将 schema 注入 systemPrompt
        var effectivePrompt = systemPrompt;
        if (outputSchema != null) {
            var schemaInstruction = "请严格按照以下 JSON Schema 格式输出：\n" + outputSchema;
            effectivePrompt =
                    effectivePrompt != null
                            ? effectivePrompt + "\n\n" + schemaInstruction
                            : schemaInstruction;
        }

        // 使用 modelId 作为 targetId（scene 的替代）
        var targetId = modelId != null ? modelId : scene;

        var request =
                new ExecutionRequest(
                        ExecutionTarget.LLM,
                        targetId,
                        input,
                        null,
                        userId,
                        effectivePrompt,
                        temperature,
                        true);
        var result = dispatcher.dispatch(request);

        execution.setVariable("output", result.output());
        execution.setVariable("success", result.success());
    }
}
