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
 * 按用户加载 ai_persona 用户画像，生成注入系统提示词的画像段。
 *
 * <p>查找优先级：
 *
 * <ol>
 *   <li>用户私有画像（owner_id=userId AND status='active'），取最近修改的一条
 *   <li>无私有画像时不注入（不使用公共默认，避免污染个人画像）
 * </ol>
 *
 * <p>注入内容：persona（偏好/习惯描述）+ 提示 Agent 在对话中主动更新画像。
 */
public class PersonaContextLoader {

    private static final Logger log = LoggerFactory.getLogger(PersonaContextLoader.class);

    private final JdbcTemplate jdbc;

    public PersonaContextLoader(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 加载用户画像，构建系统提示词附加段。
     *
     * @param userId 当前用户 ID，null 时返回空字符串
     * @return Markdown 格式的画像段，未找到时返回空字符串
     */
    public String buildPersonaPrompt(Long userId) {
        if (userId == null) return "";

        var row = loadPersona(userId);
        if (row == null) return "";

        var sb = new StringBuilder(512);
        sb.append("\n# 用户画像\n");
        sb.append("以下是当前用户的已知偏好与习惯，在创作时主动参考：\n\n");

        if (row.persona() != null && !row.persona().isBlank()) {
            sb.append(row.persona()).append("\n\n");
        }

        sb.append("**画像更新指引**：\n");
        sb.append("- 对话中发现新的稳定偏好（语气、风格、禁用词、常用平台等），立即调用 `update_persona` 工具更新；\n");
        sb.append("- 一次性偏好或当前对话特有的设定不要写入画像；\n");
        sb.append("- 画像 ID：").append(row.id()).append("（update_persona 时需要传入）\n");

        log.debug("[PersonaLoader] userId={} personaId={}", userId, row.id());
        return sb.toString();
    }

    /** 返回用户画像 id，供 UpdatePersonaTool 使用。 */
    public Long loadPersonaId(Long userId) {
        if (userId == null) return null;
        var row = loadPersona(userId);
        return row == null ? null : row.id();
    }

    private PersonaRow loadPersona(Long userId) {
        try {
            return jdbc.queryForObject(
                    """
                    SELECT id, persona FROM ai_persona
                    WHERE owner_id = ? AND status = 'active' AND deleted = false
                    ORDER BY update_time DESC LIMIT 1
                    """,
                    (rs, n) -> new PersonaRow(rs.getLong("id"), rs.getString("persona")),
                    userId);
        } catch (EmptyResultDataAccessException e) {
            return null;
        } catch (Exception e) {
            log.warn("[PersonaLoader] 查询失败 userId={}: {}", userId, e.getMessage());
            return null;
        }
    }

    private record PersonaRow(Long id, String persona) {}
}
