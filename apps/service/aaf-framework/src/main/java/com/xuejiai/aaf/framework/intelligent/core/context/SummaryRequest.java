package com.xuejiai.aaf.framework.intelligent.core.context;

/** 摘要请求。 */
public record SummaryRequest(
        String modelId,
        String systemPrompt,
        String userPromptTemplate,
        String messages,
        int budgetTokens) {}
