package com.xuejiai.aaf.module.ai.chat.agui;

import java.util.List;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.intelligent.agent.agentscope.ChatSessionResolver;
import com.xuejiai.aaf.module.ai.chat.repository.ChatMessageRepository;
import com.xuejiai.aaf.module.ai.chat.repository.ChatSessionRepository;

import lombok.RequiredArgsConstructor;

/**
 * {@link ChatSessionResolver} 实现——桥接 ChatSession/ChatMessage。
 */
@Component
@RequiredArgsConstructor
public class ChatSessionResolverImpl implements ChatSessionResolver {

    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;

    @Override
    public SessionContext resolveByThreadId(String threadId) {
        if (threadId == null || threadId.isBlank()) return null;
        return sessionRepository.findByThreadId(threadId)
                .map(s -> new SessionContext(
                        s.getCreatorId(), s.getAssistantId(), s.getKnowledgeBaseId(), s.getId()))
                .orElse(null);
    }

    @Override
    public List<HistoryMessage> loadHistory(Long sessionId) {
        if (sessionId == null) return List.of();
        return messageRepository.findBySessionIdOrderByCreateTimeAsc(sessionId).stream()
                .map(m -> new HistoryMessage(m.getRole(), m.getContent()))
                .toList();
    }
}
