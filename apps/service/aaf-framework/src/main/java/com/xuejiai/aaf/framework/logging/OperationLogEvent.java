package com.xuejiai.aaf.framework.logging;

import java.time.LocalDateTime;

/** 操作日志事件，用于异步写入数据库。 */
public record OperationLogEvent(
        Long userId,
        String username,
        String module,
        String type,
        String description,
        String bizNo,
        String requestMethod,
        String requestUrl,
        String requestParams,
        String responseResult,
        String ip,
        String userAgent,
        long durationMs,
        boolean success,
        String errorMessage,
        LocalDateTime createTime) {}
