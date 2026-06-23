/*
 * Copyright 2024-2026 xuejiai.com & AaronZZH.
 * Licensed under the Apache License, Version 2.0.
 */
package com.xuejiai.aaf.framework.agentscope.runtime;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 知识库自动注入加载器——在 Agent 启动时把标记了 {@code auto_inject=true} 的知识库 top 片段注入系统提示词。
 *
 * <p>查询范围：绑定到当前 knowledgeBaseId（主知识库）的上层目录里标记了 auto_inject 的知识库， 或者属于当前用户（{@code owner_id=userId}）且
 * {@code auto_inject=true} 的所有知识库。
 *
 * <p>每个知识库取内容最长的前 N 个 chunk，拼接后截断到总计 ≤3000 字符，注入为背景上下文。
 */
public class KbAutoInjectLoader {

    private static final Logger log = LoggerFactory.getLogger(KbAutoInjectLoader.class);
    private static final int MAX_CHARS = 3000;
    private static final int TOP_CHUNKS = 5;

    private final JdbcTemplate jdbc;

    public KbAutoInjectLoader(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 加载用户的自动注入知识库内容，构建背景上下文段。
     *
     * @param userId 当前用户 ID，null 时跳过
     * @return Markdown 格式的背景知识段，无内容时返回空字符串
     */
    public String buildAutoInjectContext(Long userId) {
        if (userId == null) return "";

        List<KbChunkRow> chunks = loadChunks(userId);
        if (chunks.isEmpty()) return "";

        var sb = new StringBuilder(MAX_CHARS + 256);
        sb.append("\n# 背景知识（自动注入）\n");
        sb.append("以下是用户标记为「自动注入」的知识库内容，可直接引用（无需调用 search_kb）：\n\n");

        int totalChars = sb.length();
        String lastKbName = null;

        for (var chunk : chunks) {
            if (totalChars >= MAX_CHARS) break;

            if (!chunk.kbName().equals(lastKbName)) {
                sb.append("## ").append(chunk.kbName()).append("\n");
                lastKbName = chunk.kbName();
            }

            String content = chunk.content();
            int remaining = MAX_CHARS - totalChars;
            if (content.length() > remaining) {
                content = content.substring(0, remaining) + "…（截断）";
            }
            sb.append(content).append("\n\n");
            totalChars += content.length();
        }

        log.debug("[KbAutoInject] userId={} 注入 {} 个片段，共 {} 字符", userId, chunks.size(), totalChars);
        return sb.toString();
    }

    private List<KbChunkRow> loadChunks(Long userId) {
        try {
            return jdbc.query(
                    """
                    SELECT kb.name AS kb_name, ck.content
                    FROM ai_knowledge_base kb
                    JOIN ai_knowledge_chunk ck ON ck.knowledge_base_id = kb.id
                    WHERE kb.auto_inject = TRUE
                      AND kb.deleted = FALSE
                      AND kb.status = 1
                      AND (kb.owner_id IS NULL OR kb.owner_id = ?)
                    ORDER BY kb.id, ck.chunk_index
                    LIMIT ?
                    """,
                    (rs, n) -> new KbChunkRow(rs.getString("kb_name"), rs.getString("content")),
                    userId,
                    TOP_CHUNKS * 5); // 多取几个以备截断
        } catch (Exception e) {
            log.warn("[KbAutoInject] 加载失败 userId={}: {}", userId, e.getMessage());
            return List.of();
        }
    }

    private record KbChunkRow(String kbName, String content) {}
}
