package com.xuejiai.aaf.module.system.workflow.vo;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 自动化规则响应。
 *
 * @author AaronZZH & Kiro
 */
public record AutomationRuleVO(
        @Schema(description = "规则 ID") Long id,
        @Schema(description = "规则名称") String name,
        @Schema(description = "实体标识") String entitySlug,
        @Schema(description = "触发器类型") String triggerType,
        @Schema(description = "触发条件（JSON）") String conditions,
        @Schema(description = "执行动作（JSON）") String actions,
        @Schema(description = "是否启用") Boolean enabled,
        @Schema(description = "创建时间") LocalDateTime createTime) {}
