package com.xuejiai.aaf.module.chat.livechat.rating.vo;

import java.time.LocalDateTime;

/**
 * 会话评价响应 VO。
 *
 * @author AaronZZH & Kiro
 */
public record RatingVO(
        Long id,
        Long conversationId,
        Long userId,
        Long staffId,
        Integer score,
        String comment,
        LocalDateTime createTime) {}
