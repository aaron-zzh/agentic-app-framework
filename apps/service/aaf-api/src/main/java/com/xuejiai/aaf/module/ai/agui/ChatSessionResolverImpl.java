package com.xuejiai.aaf.module.ai.agui;

import java.util.List;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.intelligent.core.assistant.ChatSessionResolver;
import com.xuejiai.aaf.module.chat.conversation.repository.ConversationRepository;
import com.xuejiai.aaf.module.chat.message.repository.ConversationMessageRepository;

import lombok.RequiredArgsConstructor;

/** {@link ChatSessionResolver} 实现——桥接 Conversation/ConversationMessage。 */
@Component
@RequiredArgsConstructor
public class ChatSessionResolverImpl implements ChatSessionResolver {

    private final ConversationRepository conversationRepository;
    private final ConversationMessageRepository messageRepository;

    @Override
    public SessionContext resolveByThreadId(String threadId) {
        if (threadId == null || threadId.isBlank()) return null;
        return conversationRepository
                .findByThreadId(threadId)
                .map(
                        conv ->
                                new SessionContext(
                                        conv.getCreatorId(),
                                        conv.getAssistantId() != null
                                                ? conv.getAssistantId().toString()
                                                : null,
                                        conv.getKnowledgeBaseId(),
                                        conv.getId()))
                .orElse(null);
    }

    @Override
    public List<HistoryMessage> loadHistory(Long sessionId) {
        if (sessionId == null) return List.of();
        return messageRepository.findByConversationIdOrderByCreateTimeAsc(sessionId).stream()
                .map(m -> new HistoryMessage(m.getRole(), m.getContent()))
                .toList();
    }
}
