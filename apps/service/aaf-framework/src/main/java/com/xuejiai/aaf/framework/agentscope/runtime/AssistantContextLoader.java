/*
 * Copyright 2024-2026 xuejiai.com & AaronZZH.
 * Licensed under the Apache License, Version 2.0.
 */
package com.xuejiai.aaf.framework.agentscope.runtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 按 assistantId 加载 ai_assistant + ai_persona，生成注入系统提示词的助理配置段。
 *
 * <p>查询优先级：
 *
 * <ol>
 *   <li>从 {@code ai_assistant} 读取 persona_id / knowledge_base_id / model_id
 *   <li>从 {@code ai_persona} 读取 system_prompt / persona 文本
 *   <li>assistantId 为 null 或查不到时静默返回 {@link AssistantConfig#empty()}
 * </ol>
 */
public class AssistantContextLoader {

    private static final Logger log = LoggerFactory.getLogger(AssistantContextLoader.class);

    private final JdbcTemplate jdbc;

    public AssistantContextLoader(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 加载助理配置。
     *
     * @param assistantId 助理 ID，null 时返回空配置
     * @return 助理配置，不会为 null
     */
    public AssistantConfig load(Long assistantId) {
        if (assistantId == null) return AssistantConfig.empty();

        try {
            return jdbc.queryForObject(
                    """
                    SELECT a.knowledge_base_id, a.model_id,
                           p.system_prompt, p.persona, p.name AS persona_name
                    FROM ai_assistant a
                    LEFT JOIN ai_persona p ON p.id = a.persona_id AND p.deleted = false
                    WHERE a.id = ? AND a.deleted = false AND a.status = 'active'
                    LIMIT 1
                    """,
                    (rs, n) ->
                            new AssistantConfig(
                                    assistantId,
                                    (Long) rs.getObject("knowledge_base_id"),
                                    (Long) rs.getObject("model_id"),
                                    rs.getString("system_prompt"),
                                    rs.getString("persona"),
                                    rs.getString("persona_name")),
                    assistantId);
        } catch (EmptyResultDataAccessException e) {
            log.debug("[AssistantContextLoader] assistantId={} 未找到", assistantId);
            return AssistantConfig.empty();
        } catch (Exception e) {
            log.warn(
                    "[AssistantContextLoader] 加载失败 assistantId={}: {}",
                    assistantId,
                    e.getMessage());
            return AssistantConfig.empty();
        }
    }

    /**
     * 助理配置。
     *
     * @param assistantId 助理 ID，null 表示未绑定
     * @param knowledgeBaseId 助理默认知识库 ID，null 表示未绑定
     * @param modelId 助理指定模型 ID，null 表示走 CapabilityRouter 决策链
     * @param systemPrompt 来自 ai_persona.system_prompt 的助理专属指令
     * @param persona 来自 ai_persona.persona 的人格描述文本
     * @param personaName 人格名称
     */
    public record AssistantConfig(
            Long assistantId,
            Long knowledgeBaseId,
            Long modelId,
            String systemPrompt,
            String persona,
            String personaName) {

        /** 未绑定助理时的空配置。 */
        public static AssistantConfig empty() {
            return new AssistantConfig(null, null, null, null, null, null);
        }

        /** 是否有助理专属系统提示词可注入。 */
        public boolean hasSystemPrompt() {
            return systemPrompt != null && !systemPrompt.isBlank();
        }

        /** 是否有助理专属人格描述可注入。 */
        public boolean hasPersona() {
            return persona != null && !persona.isBlank();
        }

        /**
         * 构建注入系统提示词的助理段落。
         *
         * @return Markdown 格式的助理配置段，无内容时返回空字符串
         */
        public String buildPromptSegment() {
            if (!hasSystemPrompt() && !hasPersona()) return "";

            var sb = new StringBuilder(512);
            sb.append("\n# 助理配置");
            if (personaName != null && !personaName.isBlank()) {
                sb.append("（").append(personaName).append("）");
            }
            sb.append("\n");

            if (hasPersona()) {
                sb.append(persona).append("\n\n");
            }
            if (hasSystemPrompt()) {
                sb.append(systemPrompt).append("\n");
            }
            return sb.toString();
        }
    }
}
