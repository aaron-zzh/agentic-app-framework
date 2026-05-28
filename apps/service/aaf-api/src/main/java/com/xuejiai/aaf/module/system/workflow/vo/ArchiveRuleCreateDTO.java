package com.xuejiai.aaf.module.system.workflow.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 归档规则创建/更新请求。
 *
 * @author AaronZZH & Kiro
 */
public record ArchiveRuleCreateDTO(
        @Schema(description = "实体标识", example = "order") @NotBlank String entitySlug,
        @Schema(description = "归档条件（JSON）") @NotNull String condition,
        @Schema(description = "等待天数", example = "90") Integer afterDays,
        @Schema(description = "是否启用") Boolean enabled) {}
