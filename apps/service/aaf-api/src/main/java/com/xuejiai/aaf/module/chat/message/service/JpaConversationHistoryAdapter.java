package com.xuejiai.aaf.module.chat.message.service;

import java.time.ZoneId;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.intelligent.cognition.memory.MemoryMessage;
import com.xuejiai.aaf.framework.intelligent.cognition.port.ConversationHistoryPort;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.UserId;
import com.xuejiai.aaf.module.chat.conversation.repository.ConversationRepository;
import com.xuejiai.aaf.module.chat.message.repository.ConversationMessageRepository;

import lombok.RequiredArgsConstructor;

/**
 * 会话历史只读适配器——Redis 短期记忆 TTL 失效后的数据库兜底。
 *
 * <p>只读 {@code conversation}/{@code conversation_message} 两表；不做归属校验（调用方
 * {@code RedisSessionMemoryAdapter} 传入的 {@code conversationId} 即 AG-UI {@code threadId}，
 * 其归属已在上游会话入口校验，本适配器不重复校验）。
 */
@Component
@RequiredArgsConstructor
class JpaConversationHistoryAdapter implements ConversationHistoryPort {

    private final ConversationRepository conversations;
    private final ConversationMessageRepository messages;

    @Override
    public List<MemoryMessage> recentMessages(
            TenantId tenantId, UserId userId, String conversationId, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        var conversation = conversations.findByThreadId(conversationId).orElse(null);
        if (conversation == null) {
            return List.of();
        }
        var recentDesc =
                messages.findByConversationIdAndIsInternalFalseOrderByCreateTimeDesc(
                        conversation.getId(), PageRequest.of(0, limit));
        return recentDesc.reversed().stream().map(JpaConversationHistoryAdapter::toMemoryMessage).toList();
    }

    private static MemoryMessage toMemoryMessage(
            com.xuejiai.aaf.module.chat.message.domain.ConversationMessage message) {
        var content = message.getContent();
        var timestamp =
                message.getCreateTime() == null
                        ? null
                        : message.getCreateTime().atZone(ZoneId.systemDefault()).toInstant();
        return new MemoryMessage(message.getRole(), content == null ? "" : content, timestamp);
    }
}
