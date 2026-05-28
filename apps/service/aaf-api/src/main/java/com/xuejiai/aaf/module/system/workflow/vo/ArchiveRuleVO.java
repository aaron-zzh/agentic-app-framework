package com.xuejiai.aaf.module.system.workflow.vo;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 归档规则响应。
 *
 * @author AaronZZH & Kiro
 */
public record ArchiveRuleVO(
        @Schema(description = "规则 ID") Long id,
        @Schema(description = "实体标识", example = "order") String entitySlug,
        @Schema(description = "归档条件（JSON）") String condition,
        @Schema(description = "等待天数", example = "90") Integer afterDays,
        @Schema(description = "是否启用") Boolean enabled,
        @Schema(description = "创建时间") LocalDateTime createTime) {}
