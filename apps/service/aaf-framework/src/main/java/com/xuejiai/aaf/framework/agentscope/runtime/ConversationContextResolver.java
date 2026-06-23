/*
 * Copyright 2024-2026 xuejiai.com & AaronZZH.
 * Licensed under the Apache License, Version 2.0.
 */
package com.xuejiai.aaf.framework.agentscope.runtime;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 把 AG-UI 请求层信息（forwardedProps + threadId）解析为 {@link AafContextHolder.AafContext} 的服务。
 *
 * <p>解析顺序：
 *
 * <ol>
 *   <li>优先从 {@code forwardedProps} 直接读 userId / conversationId / knowledgeBaseId / assistantId
 *   <li>缺字段时根据 {@code threadId} 查 {@code conversation} 表反查 creator_id / assistant_id /
 *       knowledge_base_id / id 兜底
 *   <li>仍然缺 userId → 抛异常（HITL 等工具不能在无用户上下文时跑）
 * </ol>
 *
 * <p>设计目标：解耦 AG-UI 层与业务层。AG-UI starter 只管 threadId，业务层（AAF）通过 conversation 表把 threadId 桥接到具体
 * user/conv/kb——这样多用户、多助理、多知识库的场景都不需要在 AG-UI 协议层修改。
 */
@Service
public class ConversationContextResolver {

    private static final Logger log = LoggerFactory.getLogger(ConversationContextResolver.class);

    private final JdbcTemplate jdbcTemplate;

    public ConversationContextResolver(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 解析 AG-UI 请求上下文。
     *
     * @param threadId AG-UI threadId（必填）
     * @param forwardedProps RunAgentInput.forwardedProps，可空
     * @param fallbackUserId 当 forwardedProps + DB 都给不出 userId 时用此（来自 OperatorContext / JWT）；null
     *     时跳过
     * @return 完整上下文。若 userId 仍为空，{@link AafContextHolder.AafContext#userId()} 返回 null
     */
    public AafContextHolder.AafContext resolve(
            String threadId, Map<String, Object> forwardedProps, Long fallbackUserId) {
        var fp = forwardedProps == null ? Map.<String, Object>of() : forwardedProps;

        Long userId = readLong(fp, "userId");
        Long conversationId = readLong(fp, "conversationId");
        Long knowledgeBaseId = readLong(fp, "knowledgeBaseId");
        Long assistantId = readLong(fp, "assistantId");
        Boolean enableThinking = readBoolean(fp, "enableThinking");
        Integer thinkingBudget = readInteger(fp, "thinkingBudget");

        // forwardedProps 不全 → 查 conversation 表兜底
        if ((userId == null
                        || conversationId == null
                        || knowledgeBaseId == null
                        || assistantId == null)
                && threadId != null
                && !threadId.isBlank()) {
            var fromDb = lookupByThreadId(threadId);
            if (fromDb != null) {
                if (userId == null) userId = fromDb.creatorId();
                if (conversationId == null) conversationId = fromDb.id();
                if (knowledgeBaseId == null) knowledgeBaseId = fromDb.knowledgeBaseId();
                if (assistantId == null) assistantId = fromDb.assistantId();
            }
        }

        // 还缺 knowledgeBaseId → 从 ai_assistant 读默认知识库
        if (knowledgeBaseId == null && assistantId != null) {
            knowledgeBaseId = lookupAssistantKbId(assistantId);
        }

        // 还缺 userId → JWT 兜底
        if (userId == null && fallbackUserId != null) {
            userId = fallbackUserId;
        }

        log.debug(
                "[ContextResolver] threadId={} → userId={} convId={} kbId={} assistantId={} enableThinking={}",
                threadId,
                userId,
                conversationId,
                knowledgeBaseId,
                assistantId,
                enableThinking);

        return new AafContextHolder.AafContext(
                userId,
                assistantId,
                conversationId,
                knowledgeBaseId,
                threadId,
                enableThinking,
                thinkingBudget);
    }

    /** 用 JdbcTemplate 查 conversation 表（避免跨模块 JPA 依赖）。 */
    private ConversationRow lookupByThreadId(String threadId) {
        try {
            return jdbcTemplate.queryForObject(
                    """
                    SELECT id, creator_id, assistant_id, knowledge_base_id
                    FROM conversation
                    WHERE thread_id = ? AND deleted = false
                    LIMIT 1
                    """,
                    (rs, n) ->
                            new ConversationRow(
                                    rs.getLong("id"),
                                    (Long) rs.getObject("creator_id"),
                                    (Long) rs.getObject("assistant_id"),
                                    (Long) rs.getObject("knowledge_base_id")),
                    threadId);
        } catch (EmptyResultDataAccessException e) {
            log.debug("[ContextResolver] threadId={} 在 conversation 表中未找到", threadId);
            return null;
        } catch (Exception e) {
            log.warn(
                    "[ContextResolver] 查询 conversation 失败 threadId={}: {}",
                    threadId,
                    e.getMessage());
            return null;
        }
    }

    /** 安全把 forwardedProps 里的值（可能是 String / Number / Long）解析成 Long。 */
    private static Long readLong(Map<String, Object> map, String key) {
        var v = map.get(key);
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        if (v instanceof String s) {
            try {
                return Long.parseLong(s.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private static Boolean readBoolean(Map<String, Object> map, String key) {
        var v = map.get(key);
        if (v == null) return null;
        if (v instanceof Boolean b) return b;
        if (v instanceof String s) return Boolean.parseBoolean(s.trim());
        return null;
    }

    private static Integer readInteger(Map<String, Object> map, String key) {
        var v = map.get(key);
        if (v == null) return null;
        if (v instanceof Number n) return n.intValue();
        if (v instanceof String s) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private record ConversationRow(
            Long id, Long creatorId, Long assistantId, Long knowledgeBaseId) {}

    /** 从 ai_assistant 表读默认知识库 ID。 */
    private Long lookupAssistantKbId(Long assistantId) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT knowledge_base_id FROM ai_assistant WHERE id = ? AND deleted = false LIMIT 1",
                    Long.class,
                    assistantId);
        } catch (Exception e) {
            log.debug("[ContextResolver] assistant {} 无默认知识库: {}", assistantId, e.getMessage());
            return null;
        }
    }
}
