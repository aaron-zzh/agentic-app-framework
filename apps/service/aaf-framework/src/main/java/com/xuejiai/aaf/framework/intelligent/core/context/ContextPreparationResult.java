package com.xuejiai.aaf.framework.intelligent.core.context;

import java.util.List;

import org.springframework.ai.chat.messages.Message;

/** 上下文预处理结果。 */
public record ContextPreparationResult(
        List<Message> messages,
        ContextBudget budget,
        int tokenBefore,
        int tokenAfter,
        List<ContextCompressionAction> actions) {}
