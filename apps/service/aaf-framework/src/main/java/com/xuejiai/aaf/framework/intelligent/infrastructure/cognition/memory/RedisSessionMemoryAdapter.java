package com.xuejiai.aaf.framework.intelligent.infrastructure.cognition.memory;

import java.util.List;
import java.util.Objects;

import com.xuejiai.aaf.framework.intelligent.cognition.memory.MemoryMessage;
import com.xuejiai.aaf.framework.intelligent.cognition.memory.ShortTermMemoryService;
import com.xuejiai.aaf.framework.intelligent.cognition.port.SessionMemoryPort;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.UserId;

/**
 * 短期会话记忆适配器——把 Redis 短期记忆暴露为 L1 读管道的一个通道。
 *
 * <p>只做读取与形态转换，不写入、不延长 TTL；空白或非法角色的记录直接丢弃，避免污染受控上下文。
 */
public final class RedisSessionMemoryAdapter implements SessionMemoryPort {

    private static final String ROLE_USER = "user";
    private static final String ROLE_ASSISTANT = "assistant";

    private final ShortTermMemoryService shortTermMemories;

    public RedisSessionMemoryAdapter(ShortTermMemoryService shortTermMemories) {
        this.shortTermMemories =
                Objects.requireNonNull(shortTermMemories, "shortTermMemories 不能为空");
    }

    @Override
    public List<SessionTurn> recentTurns(SessionRecallQuery query) {
        Objects.requireNonNull(query, "query 不能为空");
        var ownerId = numericOwner(query.userId());
        if (ownerId == null) {
            // 访客等非数值主体没有短期会话槽位，按无历史处理
            return List.of();
        }
        var messages =
                shortTermMemories.getContext(
                        query.tenantId().value(), ownerId, query.sessionId(), query.maxTurns());
        return messages.stream()
                .filter(message -> message != null && normalizedRole(message.role()) != null)
                .filter(message -> message.content() != null && !message.content().isBlank())
                .map(
                        message ->
                                new SessionTurn(
                                        normalizedRole(message.role()), message.content().trim()))
                .toList();
    }

    @Override
    public void appendTurn(
            TenantId tenantId, UserId userId, String sessionId, String userText, String replyText) {
        if (tenantId == null || userId == null || sessionId == null || sessionId.isBlank()) {
            return;
        }
        var ownerId = numericOwner(userId);
        if (ownerId == null) {
            return;
        }
        if (userText != null && !userText.isBlank()) {
            shortTermMemories.append(
                    tenantId.value(),
                    ownerId,
                    sessionId,
                    new MemoryMessage(ROLE_USER, userText.trim()));
        }
        if (replyText != null && !replyText.isBlank()) {
            shortTermMemories.append(
                    tenantId.value(),
                    ownerId,
                    sessionId,
                    new MemoryMessage(ROLE_ASSISTANT, replyText.trim()));
        }
    }

    /** 访客等非数值主体没有短期会话槽位。 */
    private static Long numericOwner(UserId userId) {
        try {
            return Long.valueOf(userId.value());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    /** 只接受 user 与 assistant 两种角色；system 与异常记录不进入受控上下文。 */
    private static String normalizedRole(String role) {
        if (role == null) {
            return null;
        }
        var normalized = role.trim().toLowerCase(java.util.Locale.ROOT);
        return switch (normalized) {
            case ROLE_USER -> ROLE_USER;
            case ROLE_ASSISTANT -> ROLE_ASSISTANT;
            default -> null;
        };
    }
}
