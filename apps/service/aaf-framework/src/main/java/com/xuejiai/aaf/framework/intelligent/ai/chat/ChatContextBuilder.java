/**
 * 对话上下文构建器。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.framework.intelligent.ai.chat;

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

/**
 * 构建发送给 LLM 的消息列表，实现滑动窗口上下文管理。
 *
 * <p>Token 估算采用简单的字符数/4 策略，满足大多数场景。
 */
@Component
public class ChatContextBuilder {

    /**
     * 构建消息列表（滑动窗口）。
     *
     * @param systemPrompt 系统提示词
     * @param historyMessages 历史消息（role + content 对），按时间正序
     * @param userInput 当前用户输入
     * @param maxTokens 上下文窗口最大 Token 数
     * @return 发送给 LLM 的消息列表
     */
    public List<Message> buildMessages(
            String systemPrompt,
            List<HistoryMessage> historyMessages,
            String userInput,
            int maxTokens) {

        var result = new ArrayList<Message>();

        // 系统提示词固定占位
        int usedTokens = estimateTokens(systemPrompt) + estimateTokens(userInput);

        // 从最新消息往前取，直到超限
        var selected = new ArrayList<HistoryMessage>();
        for (int i = historyMessages.size() - 1; i >= 0; i--) {
            var msg = historyMessages.get(i);
            int msgTokens = estimateTokens(msg.content());
            if (usedTokens + msgTokens > maxTokens) {
                break;
            }
            usedTokens += msgTokens;
            selected.addFirst(msg);
        }

        // 组装：system → history → user
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            result.add(new SystemMessage(systemPrompt));
        }
        for (var msg : selected) {
            result.add(toMessage(msg));
        }
        result.add(new UserMessage(userInput));

        return result;
    }

    /** 简单 Token 估算：字符数 / 4 */
    private int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return text.length() / 4 + 1;
    }

    private Message toMessage(HistoryMessage msg) {
        return switch (msg.role()) {
            case "assistant" -> new AssistantMessage(msg.content());
            case "system" -> new SystemMessage(msg.content());
            default -> new UserMessage(msg.content());
        };
    }

    /** 历史消息记录 */
    public record HistoryMessage(String role, String content) {}
}
