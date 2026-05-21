package com.xuejiai.aaf.module.system.workflow.vo;

import java.time.LocalDateTime;

/** 自动化执行日志响应。 */
public record AutomationLogVO(
        Long id,
        Long ruleId,
        String triggerType,
        String entityType,
        Long entityId,
        String status,
        String errorMessage,
        LocalDateTime executedAt) {}
