package com.xuejiai.aaf.module.system.log.vo;

import java.time.LocalDateTime;

/** 操作日志响应 VO。 */
public record OperationLogVO(
        Long id,
        Long userId,
        String username,
        String module,
        String type,
        String description,
        String bizNo,
        String requestMethod,
        String requestUrl,
        Long durationMs,
        Boolean success,
        String errorMessage,
        LocalDateTime createTime) {}
