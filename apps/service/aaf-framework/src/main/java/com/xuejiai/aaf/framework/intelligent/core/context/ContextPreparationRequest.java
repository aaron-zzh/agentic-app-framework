package com.xuejiai.aaf.framework.intelligent.core.context;

import java.util.List;

import org.springframework.ai.chat.messages.Message;

/** 上下文预处理请求。 */
public record ContextPreparationRequest(
        List<Message> messages,
        String modelId,
        Long userId,
        String policy) {}
