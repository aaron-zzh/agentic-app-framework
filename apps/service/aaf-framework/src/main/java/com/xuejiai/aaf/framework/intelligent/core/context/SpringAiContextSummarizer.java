package com.xuejiai.aaf.framework.intelligent.core.context;

import java.util.List;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import com.xuejiai.aaf.framework.intelligent.ai.chat.DynamicChatClientFactory;

import lombok.RequiredArgsConstructor;

/** 基于 Spring AI ChatModel 的上下文摘要器。 */
@Service
@RequiredArgsConstructor
public class SpringAiContextSummarizer implements ContextSummarizer {

    private final DynamicChatClientFactory clientFactory;

    @Override
    public SummaryResult summarize(SummaryRequest request) {
        var userPrompt =
                request.userPromptTemplate()
                        .replace("${budgetTokens}", String.valueOf(request.budgetTokens()))
                        .replace("${messages}", request.messages());
        List<Message> messages =
                List.of(new SystemMessage(request.systemPrompt()), new UserMessage(userPrompt));
        var response =
                clientFactory.get(request.modelId()).prompt(new Prompt(messages)).call().chatResponse();
        var content = response.getResult().getOutput().getText();
        return new SummaryResult(content != null ? content.trim() : "", request.modelId());
    }
}
