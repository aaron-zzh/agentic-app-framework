package com.xuejiai.aaf.framework.intelligent.infrastructure.workflow.node;

import java.util.List;

import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.intelligent.ai.chat.ResilientChatService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** LLM 节点——通过统一模型路由、积分计量和降级链路执行提示词。 */
@Slf4j
@Component("llmNode")
@RequiredArgsConstructor
public class LlmNode implements JavaDelegate {

    private final ResilientChatService chatService;

    @Override
    public void execute(DelegateExecution execution) {
        var prompt = stringVariable(execution, "prompt", "{{input}}");
        var input = stringVariable(execution, "input", "");
        var systemPrompt = stringVariable(execution, "systemPrompt", "");
        var modelId = stringVariable(execution, "modelId", null);
        var userId = longVariable(execution, "_aafUserId");
        var renderedPrompt = prompt.replace("{{input}}", input);

        try {
            var messages = new java.util.ArrayList<org.springframework.ai.chat.messages.Message>();
            if (!systemPrompt.isBlank()) {
                messages.add(new SystemMessage(systemPrompt));
            }
            messages.add(new UserMessage(renderedPrompt));
            var response = chatService.call(List.copyOf(messages), modelId, userId);
            var output = response.getResult().getOutput().getText();
            execution.setVariable("output", output);
            execution.setVariable("success", true);
        } catch (RuntimeException failure) {
            log.error("LLM 节点执行失败: nodeId={}", execution.getCurrentActivityId(), failure);
            execution.setVariable("success", false);
            execution.setVariable("error", failure.getMessage());
        }
    }

    private String stringVariable(DelegateExecution execution, String name, String defaultValue) {
        var value = execution.getVariable(name);
        return value == null ? defaultValue : String.valueOf(value);
    }

    private Long longVariable(DelegateExecution execution, String name) {
        var value = execution.getVariable(name);
        if (value instanceof Number number) {
            return number.longValue();
        }
        return value == null ? null : Long.valueOf(String.valueOf(value));
    }
}
