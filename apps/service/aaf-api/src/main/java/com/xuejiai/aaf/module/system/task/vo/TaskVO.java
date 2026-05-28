package com.xuejiai.aaf.module.system.task.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 任务列表展示 VO。
 *
 * @author AaronZZH & Kiro
 */
public record TaskVO(
        @Schema(description = "任务名称") String name,
        @Schema(description = "Cron 表达式") String cronExpression,
        @Schema(description = "是否启用") boolean enabled,
        @Schema(description = "任务描述") String description) {}
