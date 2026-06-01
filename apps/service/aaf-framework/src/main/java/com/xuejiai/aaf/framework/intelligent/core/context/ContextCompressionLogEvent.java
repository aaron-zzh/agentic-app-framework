package com.xuejiai.aaf.framework.intelligent.core.context;

import java.util.List;

/** 上下文压缩日志事件。 */
public record ContextCompressionLogEvent(
        Long userId,
        String modelId,
        ContextPolicy policy,
        int contextWindow,
        int inputBudget,
        int triggerTokens,
        int tokenBefore,
        int tokenAfter,
        int messageCountBefore,
        int messageCountAfter,
        List<ContextCompressionAction> actions,
        String summaryModelId,
        long durationMs,
        String reason) {}
