package com.xuejiai.aaf.module.system.vo;

import java.time.LocalDateTime;

/** 通知响应。 */
public record NotificationVO(
        Long id,
        Long userId,
        String type,
        String title,
        String body,
        String entityType,
        Long entityId,
        Boolean isRead,
        LocalDateTime createTime) {}
