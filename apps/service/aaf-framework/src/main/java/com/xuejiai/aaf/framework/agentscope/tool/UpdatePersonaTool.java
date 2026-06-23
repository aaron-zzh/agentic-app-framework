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
 * 用户画像更新工具——把对话中发现的稳定偏好写入 {@code ai_persona.persona} 字段。
 *
 * <p>Agent 在对话中发现以下类型的稳定偏好时应主动调用：
 *
 * <ul>
 *   <li>语气/风格偏好（正式/轻松/幽默）
 *   <li>平台/渠道偏好（小红书/微信公众号/B站）
 *   <li>禁用词或品牌词
 *   <li>内容长度、排版风格
 * </ul>
 *
 * <p>实现：在原有 persona 内容基础上**追加**新偏好（不覆盖），避免丢失历史数据。
 */
public class UpdatePersonaTool {

    private static final Logger log = LoggerFactory.getLogger(UpdatePersonaTool.class);

    private final JdbcTemplate jdbc;

    public UpdatePersonaTool(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Tool(
            description =
                    "更新当前用户的画像（偏好/习惯）。当对话中发现用户的稳定偏好（语气/风格/禁用词/常用平台等）时调用，"
                            + "将新偏好追加到用户画像。一次性偏好不要写入。"
                            + "需要传入系统提示词中提供的 personaId。")
    public String update_persona(
            @ToolParam(name = "personaId", description = "用户画像 ID（来自系统提示词的画像段）") long personaId,
            @ToolParam(name = "newPreference", description = "本次发现的新偏好描述，如「偏好小红书风格，喜欢用 emoji，段落简短」")
                    String newPreference) {

        Long userId = AafContextHolder.userId();
        if (userId == null) {
            return "{\"status\":\"error\",\"message\":\"当前上下文无 userId\"}";
        }
        if (newPreference == null || newPreference.isBlank()) {
            return "{\"status\":\"error\",\"message\":\"newPreference 不能为空\"}";
        }

        try {
            // 校验 personaId 属于当前用户
            var count =
                    jdbc.queryForObject(
                            "SELECT COUNT(*) FROM ai_persona WHERE id = ? AND owner_id = ? AND deleted = false",
                            Long.class,
                            personaId,
                            userId);
            if (count == null || count == 0) {
                return "{\"status\":\"error\",\"message\":\"personaId 不存在或无权限\"}";
            }

            // 追加到 persona 字段（换行分隔）
            int updated =
                    jdbc.update(
                            """
                    UPDATE ai_persona
                    SET persona = CASE
                          WHEN persona IS NULL OR persona = '' THEN ?
                          ELSE persona || E'\\n' || ?
                        END,
                        update_time = CURRENT_TIMESTAMP
                    WHERE id = ? AND owner_id = ?
                    """,
                            newPreference,
                            newPreference,
                            personaId,
                            userId);

            if (updated > 0) {
                log.info("[UpdatePersona] 画像已更新 userId={} personaId={}", userId, personaId);
                return "{\"status\":\"ok\",\"personaId\":" + personaId + ",\"message\":\"画像已更新\"}";
            } else {
                return "{\"status\":\"error\",\"message\":\"更新失败，请检查 personaId\"}";
            }
        } catch (Exception e) {
            log.warn(
                    "[UpdatePersona] 更新失败 userId={} personaId={}: {}",
                    userId,
                    personaId,
                    e.getMessage());
            return "{\"status\":\"error\",\"message\":\"更新失败: " + e.getMessage() + "\"}";
        }
    }
}
