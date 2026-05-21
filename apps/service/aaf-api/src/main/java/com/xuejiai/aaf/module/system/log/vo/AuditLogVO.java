package com.xuejiai.aaf.module.system.log.vo;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

/** 审计日志响应。 */
@Schema(description = "审计日志")
public record AuditLogVO(
        @Schema(description = "ID") Long id,
        @Schema(description = "实体类型") String entityType,
        @Schema(description = "实体 ID") Long entityId,
        @Schema(description = "操作类型") String action,
        @Schema(description = "操作用户 ID") Long userId,
        @Schema(description = "变更详情 JSON") String changes,
        @Schema(description = "IP 地址") String ip,
        @Schema(description = "创建时间") LocalDateTime createdAt) {}
