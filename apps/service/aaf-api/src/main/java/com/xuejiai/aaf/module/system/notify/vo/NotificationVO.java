package com.xuejiai.aaf.module.system.notify.vo;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 通知响应。
 *
 * @author AaronZZH & Kiro
 */
public record NotificationVO(
        @Schema(description = "主键 ID") Long id,
        @Schema(description = "用户 ID") Long userId,
        @Schema(description = "类型") String type,
        @Schema(description = "标题") String title,
        String body,
        @Schema(description = "实体类型") String entityType,
        @Schema(description = "实体 ID") Long entityId,
        Boolean isRead,
        @Schema(description = "创建时间") LocalDateTime createTime) {}
