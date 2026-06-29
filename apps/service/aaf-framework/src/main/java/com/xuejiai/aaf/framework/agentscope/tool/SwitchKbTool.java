/*
 * Copyright 2024-2026 xuejiai.com & AaronZZH.
 * Licensed under the Apache License, Version 2.0.
 */
package com.xuejiai.aaf.framework.agentscope.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import com.xuejiai.aaf.framework.agentscope.runtime.AafContextHolder;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;

/**
 * 对话中切换知识库工具。
 *
 * <p>用户说"换到知识库 5"或"用我的品牌知识库"时，模型调用此工具更新 {@link AafContextHolder} 的 knowledgeBaseId， 后续 {@code
 * search_kb} 自动使用新 ID。
 *
 * <p>使用 JdbcTemplate 验证知识库是否存在并属于当前用户（或公共知识库），避免越权访问。
 */
public class SwitchKbTool {

    private static final Logger log = LoggerFactory.getLogger(SwitchKbTool.class);

    private final JdbcTemplate jdbc;

    public SwitchKbTool(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Tool(
            description =
                    "切换当前对话绑定的知识库。用户说「换知识库」「用知识库 X」「切到 XXX 知识库」时调用。" + "切换后 search_kb 会自动检索新知识库。")
    public String switch_kb(
            @ToolParam(name = "knowledgeBaseId", description = "目标知识库 ID（数字）")
                    long knowledgeBaseId) {
        Long userId = AafContextHolder.userId();

        // 验证知识库存在 + 用户有权限（owner_id=userId 或 owner_id=null 的公共库）
        try {
            var count =
                    jdbc.queryForObject(
                            """
                    SELECT COUNT(*) FROM ai_knowledge_base
                    WHERE id = ? AND deleted = FALSE
                      AND (owner_id IS NULL OR owner_id = ?)
                    """,
                            Long.class,
                            knowledgeBaseId,
                            userId);
            if (count == null || count == 0) {
                return "{\"status\":\"error\",\"message\":\"知识库不存在或无权限访问\"}";
            }
        } catch (Exception e) {
            log.warn("[SwitchKb] 校验失败 kbId={}: {}", knowledgeBaseId, e.getMessage());
            return "{\"status\":\"error\",\"message\":\"校验知识库失败: " + e.getMessage() + "\"}";
        }

        // 更新当前 thread 的上下文
        var old = AafContextHolder.get();
        if (old != null) {
            AafContextHolder.set(
                    new AafContextHolder.AafContext(
                            old.userId(),
                            old.assistantId(),
                            old.conversationId(),
                            knowledgeBaseId,
                            old.threadId(),
                            old.enableThinking(),
                            old.thinkingBudget(),
                            old.modelId()));
        }

        log.info(
                "[SwitchKb] threadId={} userId={} kbId {} → {}",
                AafContextHolder.threadId(),
                userId,
                old != null ? old.knowledgeBaseId() : null,
                knowledgeBaseId);
        return "{\"status\":\"ok\",\"knowledgeBaseId\":"
                + knowledgeBaseId
                + ",\"message\":\"知识库已切换，后续检索将使用新知识库\"}";
    }
}
