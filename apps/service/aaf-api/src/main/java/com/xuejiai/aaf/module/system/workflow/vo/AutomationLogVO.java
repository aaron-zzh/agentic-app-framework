package com.xuejiai.aaf.module.system.workflow.vo;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 自动化执行日志响应。
 *
 * @author AaronZZH & Kiro
 */
public record AutomationLogVO(
        @Schema(description = "日志 ID") Long id,
        @Schema(description = "规则 ID") Long ruleId,
        @Schema(description = "触发器类型") String triggerType,
        @Schema(description = "实体类型") String entityType,
        @Schema(description = "实体 ID") Long entityId,
        @Schema(description = "执行状态") String status,
        @Schema(description = "错误信息") String errorMessage,
        @Schema(description = "执行时间") LocalDateTime executedAt) {}
