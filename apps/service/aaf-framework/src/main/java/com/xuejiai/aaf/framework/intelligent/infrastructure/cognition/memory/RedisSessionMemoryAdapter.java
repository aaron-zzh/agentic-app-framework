package com.xuejiai.aaf.framework.intelligent.infrastructure.cognition.memory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.xuejiai.aaf.framework.intelligent.cognition.memory.MemoryMessage;
import com.xuejiai.aaf.framework.intelligent.cognition.memory.SessionSummary;
import com.xuejiai.aaf.framework.intelligent.cognition.memory.ShortTermMemoryService;
import com.xuejiai.aaf.framework.intelligent.cognition.port.ConversationHistoryPort;
import com.xuejiai.aaf.framework.intelligent.cognition.port.SessionContextCompressionPort;
import com.xuejiai.aaf.framework.intelligent.cognition.port.SessionMemoryPort;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.UserId;

import lombok.extern.slf4j.Slf4j;

/**
 * 短期会话记忆适配器——把 Redis 短期记忆暴露为 L1 读管道的一个通道。
 *
 * <p>分层增量压缩（方案 C，2026-08-29 拍板）：原文条数超过 {@link #SUMMARY_TRIGGER_MESSAGE_COUNT} 时，
 * 对被挤出最近 {@link #RETAINED_RAW_MESSAGE_COUNT} 条窗口之外的旧轮次异步生成结构化摘要并冻结；
 * 召回时返回"已冻结摘要 + 最近原文"，摘要以低信任 USER 角色单独一条呈现，不与原文混排改写。
 *
 * <p>摘要生成失败、超时或压缩端口未装配时静默降级为纯原文召回，不阻断主执行；旧摘要在新摘要生成成功前保持有效。
 */
@Slf4j
public final class RedisSessionMemoryAdapter implements SessionMemoryPort {

    private static final String ROLE_USER = "user";
    private static final String ROLE_ASSISTANT = "assistant";
    private static final String ROLE_SUMMARY = "summary";

    /** 原文条数超过该阈值才触发一次异步摘要；未超过时纯原文召回，不产生模型调用。 */
    private static final int SUMMARY_TRIGGER_MESSAGE_COUNT = 40;

    /** 摘要只覆盖被挤出该窗口之外的旧轮次；窗口内消息始终保留原文精度，与 {@link SessionMemoryPort#DEFAULT_MAX_TURNS} 一致，避免裁剪吃掉召回还要读取的轮次。 */
    private static final int RETAINED_RAW_MESSAGE_COUNT = SessionMemoryPort.DEFAULT_MAX_TURNS;

    private final ShortTermMemoryService shortTermMemories;
    private final SessionContextCompressionPort compressor;
    private final ConversationHistoryPort history;

    public RedisSessionMemoryAdapter(ShortTermMemoryService shortTermMemories) {
        this(shortTermMemories, null, null);
    }

    public RedisSessionMemoryAdapter(
            ShortTermMemoryService shortTermMemories, SessionContextCompressionPort compressor) {
        this(shortTermMemories, compressor, null);
    }

    public RedisSessionMemoryAdapter(
            ShortTermMemoryService shortTermMemories,
            SessionContextCompressionPort compressor,
            ConversationHistoryPort history) {
        this.shortTermMemories =
                Objects.requireNonNull(shortTermMemories, "shortTermMemories 不能为空");
        this.compressor = compressor;
        this.history = history;
    }

    @Override
    public List<SessionTurn> recentTurns(SessionRecallQuery query) {
        Objects.requireNonNull(query, "query 不能为空");
        var ownerId = numericOwner(query.userId());
        if (ownerId == null) {
            // 访客等非数值主体没有短期会话槽位，按无历史处理
            return List.of();
        }
        var tenantId = query.tenantId().value();
        var turns = new ArrayList<SessionTurn>();
        var summary = shortTermMemories.getSummary(tenantId, ownerId, query.sessionId());
        if (summary != null) {
            turns.add(new SessionTurn(ROLE_SUMMARY, summary.content()));
        }
        var messages =
                shortTermMemories.getContext(tenantId, ownerId, query.sessionId(), query.maxTurns());
        if (summary == null && messages.isEmpty()) {
            // Redis 短期记忆 TTL 已失效（或从未写入）：从会话历史兜底一次，并回填 Redis 避免每轮都查数据库。
            messages = fallbackToHistory(query, tenantId, ownerId);
        }
        messages.stream()
                .filter(message -> message != null && normalizedRole(message.role()) != null)
                .filter(message -> message.content() != null && !message.content().isBlank())
                .map(
                        message ->
                                new SessionTurn(
                                        normalizedRole(message.role()), message.content().trim()))
                .forEach(turns::add);
        return List.copyOf(turns);
    }

    /** Redis 未命中时从会话历史数据库兜底读取最近若干条，并回填 Redis；找不到历史端口或查询失败按无历史降级。 */
    private List<MemoryMessage> fallbackToHistory(
            SessionRecallQuery query, String tenantId, Long ownerId) {
        if (history == null) {
            return List.of();
        }
        try {
            var recovered =
                    history.recentMessages(
                            query.tenantId(), query.userId(), query.sessionId(), query.maxTurns());
            if (!recovered.isEmpty()) {
                recovered.forEach(
                        message ->
                                shortTermMemories.append(
                                        tenantId, ownerId, query.sessionId(), message));
                log.debug(
                        "[会话历史兜底] Redis 短期记忆已失效，从会话历史回填：sessionId={}，条数={}",
                        query.sessionId(),
                        recovered.size());
            }
            return recovered;
        } catch (RuntimeException exception) {
            log.warn("[会话历史兜底] 查询失败，本轮按无历史继续：{}", exception.getMessage());
            return List.of();
        }
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
        maybeTriggerSummary(tenantId.value(), ownerId, sessionId);
    }

    /** 超过阈值时异步生成一次摘要，fire-and-forget；失败只记录日志，不影响本轮写入结果。 */
    private void maybeTriggerSummary(String tenantId, Long ownerId, String sessionId) {
        if (compressor == null) {
            return;
        }
        var size = shortTermMemories.size(tenantId, ownerId, sessionId);
        if (size <= SUMMARY_TRIGGER_MESSAGE_COUNT) {
            return;
        }
        var toSummarizeCount = (int) size - RETAINED_RAW_MESSAGE_COUNT;
        if (toSummarizeCount <= 0) {
            return;
        }
        Thread.ofVirtual()
                .name("aaf-session-summary-trigger")
                .start(() -> generateAndFreezeSummary(tenantId, ownerId, sessionId, toSummarizeCount));
    }

    private void generateAndFreezeSummary(
            String tenantId, Long ownerId, String sessionId, int toSummarizeCount) {
        try {
            var all = shortTermMemories.getAll(tenantId, ownerId, sessionId);
            if (all.size() <= toSummarizeCount) {
                return;
            }
            var oldTurns = all.subList(0, toSummarizeCount);
            var previous =
                    Optional.ofNullable(shortTermMemories.getSummary(tenantId, ownerId, sessionId));
            var summary = compressor.summarize(oldTurns, previous, ownerId);
            shortTermMemories.putSummary(tenantId, ownerId, sessionId, summary);
            // 摘要已吸收这批旧轮次的语义，裁剪掉已被覆盖的原文，避免摘要与原文重复发送给 LLM。
            shortTermMemories.trimToRecent(
                    tenantId, ownerId, sessionId, RETAINED_RAW_MESSAGE_COUNT);
        } catch (RuntimeException exception) {
            // 摘要失败保留旧摘要与最新原文尾部，不阻断主执行
            log.warn("[会话摘要] 异步生成失败，本轮继续沿用旧摘要：{}", exception.getMessage());
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
