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
 * 按用户加载技能，生成注入系统提示词的技能指令段。
 *
 * <p>查询范围：全局技能（{@code owner_id IS NULL}） + 用户私有技能（{@code owner_id=userId}）， 仅取 {@code
 * status='active'} 且 {@code category='COPYWRITING'}（内容创作相关）的技能。
 *
 * <p>注入格式（Markdown）：
 *
 * <pre>
 * # 可用技能
 * ## 小红书文案（voiceover）
 * 触发词：小红书, 红书文案
 * 指令：...
 * </pre>
 */
public class SkillContextLoader {

    private static final Logger log = LoggerFactory.getLogger(SkillContextLoader.class);

    private final JdbcTemplate jdbc;

    public SkillContextLoader(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 加载用户技能并构建系统提示词附加段。
     *
     * @param userId 当前用户 ID，null 时只加载全局技能
     * @param category 技能分类（如 "COPYWRITING"），null 时不过滤分类
     * @return Markdown 格式的技能段，无技能时返回空字符串
     */
    public String buildSkillPrompt(Long userId, String category) {
        List<SkillRow> skills = loadSkills(userId, category);
        if (skills.isEmpty()) return "";

        var sb = new StringBuilder(1024);
        sb.append("\n# 可用技能\n");
        sb.append("以下技能可按用户意图自动激活，匹配对应触发词时切换到技能专属模式：\n\n");

        for (var skill : skills) {
            sb.append("## ").append(skill.name());
            if (skill.code() != null) sb.append("（").append(skill.code()).append("）");
            sb.append("\n");
            if (skill.triggerIntent() != null && !skill.triggerIntent().isBlank()) {
                sb.append("**触发词**：").append(skill.triggerIntent()).append("\n");
            }
            if (skill.instructions() != null && !skill.instructions().isBlank()) {
                sb.append(skill.instructions()).append("\n");
            } else if (skill.systemPrompt() != null && !skill.systemPrompt().isBlank()) {
                sb.append(skill.systemPrompt()).append("\n");
            }
            sb.append("\n");
        }

        log.debug(
                "[SkillContextLoader] userId={} category={} 加载技能数={}",
                userId,
                category,
                skills.size());
        return sb.toString();
    }

    private List<SkillRow> loadSkills(Long userId, String category) {
        try {
            return jdbc.query(
                    """
                    SELECT code, name, trigger_intent, instructions, system_prompt
                    FROM ai_skill_definition
                    WHERE status = 'active'
                      AND (owner_id IS NULL OR owner_id = ?)
                      AND (? IS NULL OR category = ?)
                    ORDER BY priority DESC
                    LIMIT 20
                    """,
                    (rs, n) ->
                            new SkillRow(
                                    rs.getString("code"),
                                    rs.getString("name"),
                                    rs.getString("trigger_intent"),
                                    rs.getString("instructions"),
                                    rs.getString("system_prompt")),
                    userId,
                    category,
                    category);
        } catch (Exception e) {
            log.warn("[SkillContextLoader] 加载技能失败 userId={}: {}", userId, e.getMessage());
            return List.of();
        }
    }

    private record SkillRow(
            String code,
            String name,
            String triggerIntent,
            String instructions,
            String systemPrompt) {}
}
