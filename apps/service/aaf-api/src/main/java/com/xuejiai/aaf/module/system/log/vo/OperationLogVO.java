package com.xuejiai.aaf.module.system.log.vo;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 操作日志响应 VO。
 *
 * @author AaronZZH & Kiro
 */
public record OperationLogVO(
        @Schema(description = "主键 ID") Long id,
        @Schema(description = "用户 ID") Long userId,
        @Schema(description = "用户名") String username,
        @Schema(description = "模块") String module,
        @Schema(description = "类型") String type,
        @Schema(description = "描述") String description,
        String bizNo,
        String requestMethod,
        @Schema(description = "请求 URL") String requestUrl,
        Long durationMs,
        Boolean success,
        String errorMessage,
        @Schema(description = "创建时间") LocalDateTime createTime) {}
