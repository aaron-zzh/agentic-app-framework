package com.xuejiai.aaf.module.system.vo;

import java.time.LocalDateTime;

/** 聊天会话响应。 */
public record ChatSessionVO(
        Long id,
        String title,
        String type,
        String status,
        Long creatorId,
        LocalDateTime createTime,
        LocalDateTime updateTime) {}
