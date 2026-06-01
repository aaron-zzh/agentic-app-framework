package com.xuejiai.aaf.framework.intelligent.core.context;

import java.util.List;

import org.springframework.ai.chat.messages.Message;

/** 上下文 Token 估算器。 */
public class ContextTokenEstimator {

    private static final double CHARS_PER_TOKEN = 2.5;
    private static final int MESSAGE_OVERHEAD = 5;

    public int estimate(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        return messages.stream().mapToInt(this::estimate).sum();
    }

    public int estimate(Message message) {
        if (message == null) {
            return 0;
        }
        return MESSAGE_OVERHEAD + estimate(message.getText());
    }

    public int estimate(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return (int) Math.ceil(text.length() / CHARS_PER_TOKEN);
    }
}
