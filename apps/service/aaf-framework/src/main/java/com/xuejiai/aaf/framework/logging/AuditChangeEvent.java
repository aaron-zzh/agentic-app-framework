package com.xuejiai.aaf.framework.logging;

import java.time.LocalDateTime;
import java.util.Map;

/** 审计变更事件，由 AuditLogInterceptor 发布，业务层监听持久化。 */
public record AuditChangeEvent(
        String entityType,
        Long entityId,
        String action,
        Map<String, Object> before,
        Map<String, Object> after,
        LocalDateTime timestamp) {}
