package com.xuejiai.aaf.module.ai.chat.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 创建聊天会话请求
 *
 * @author AaronZZH & Kiro
 */
public record ChatSessionCreateDTO(
        @Schema(description = "会话标题", example = "新对话") String title,
        @Schema(description = "会话类型", example = "AI") String type) {}
