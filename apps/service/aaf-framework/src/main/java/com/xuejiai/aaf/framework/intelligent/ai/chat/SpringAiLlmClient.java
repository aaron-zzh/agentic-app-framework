package com.xuejiai.aaf.framework.intelligent.ai.chat;

import java.util.List;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.intelligent.core.llm.LlmClient;
import com.xuejiai.aaf.framework.intelligent.core.model.CapabilityRoutingContext;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;

/** LlmClient 实现——委托 ResilientChatService（Spring AI）。 */
@Component
@RequiredArgsConstructor
public class SpringAiLlmClient implements LlmClient {

    private final ResilientChatService chatService;

    @Override
    public String call(List<LlmMessage> messages, String scene, Long userId) {
        var springMessages = toSpringMessages(messages);
        var ctx = CapabilityRoutingContext.ofCapability(userId, scene != null ? scene : "CHAT");
        var response = chatService.call(springMessages, ctx);
        return response.getResult().getOutput().getText();
    }

    @Override
    public Flux<String> stream(List<LlmMessage> messages, String scene, Long userId) {
        var springMessages = toSpringMessages(messages);
        var ctx = CapabilityRoutingContext.ofCapability(userId, scene != null ? scene : "CHAT");
        return chatService.stream(springMessages, ctx)
                .map(response -> response.getResult().getOutput().getText())
                .filter(text -> text != null);
    }

    private List<Message> toSpringMessages(List<LlmMessage> messages) {
        return messages.stream().map(this::toSpringMessage).toList();
    }

    private Message toSpringMessage(LlmMessage msg) {
        return switch (msg.role()) {
            case "system" -> new SystemMessage(msg.content());
            case "assistant" -> new AssistantMessage(msg.content());
            default -> new UserMessage(msg.content());
        };
    }
}
