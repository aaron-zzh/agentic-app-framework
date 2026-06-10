package com.xuejiai.aaf.module.chat.livechat.rating.vo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 创建会话评价请求 DTO。
 *
 * @author AaronZZH & Kiro
 */
public record RatingCreateDTO(
        @NotNull Long conversationId,
        Long staffId,
        @NotNull @Min(1) @Max(5) Integer score,
        String comment) {}
