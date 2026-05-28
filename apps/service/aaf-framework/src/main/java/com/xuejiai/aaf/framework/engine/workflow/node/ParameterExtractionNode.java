package com.xuejiai.aaf.framework.engine.workflow.node;

import java.util.List;

import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.intelligent.core.llm.LlmClient;
import com.xuejiai.aaf.framework.intelligent.core.llm.LlmClient.LlmMessage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 参数提取节点——通过 LLM 从文本中结构化提取参数。
 *
 * <p>流程变量：input（待提取文本）、extractionPrompt（提取指令）、output（提取结果 JSON）
 */
@Slf4j
@Component("parameterExtractionNode")
@RequiredArgsConstructor
public class ParameterExtractionNode implements JavaDelegate {

    private final LlmClient llmClient;

    @Override
    public void execute(DelegateExecution execution) {
        var input = (String) execution.getVariable("input");
        var extractionPrompt = (String) execution.getVariable("extractionPrompt");

        var systemPrompt = """
                你是一个参数提取助手。根据用户的提取指令，从输入文本中提取结构化参数，以 JSON 格式返回。
                提取指令：%s
                """.formatted(extractionPrompt);

        var messages = List.of(LlmMessage.system(systemPrompt), LlmMessage.user(input));
        var result = llmClient.call(messages, "CHAT", null);

        execution.setVariable("output", result);
        execution.setVariable("success", true);
    }
}
