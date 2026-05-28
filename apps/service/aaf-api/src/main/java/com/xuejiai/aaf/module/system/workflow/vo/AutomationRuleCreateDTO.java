package com.xuejiai.aaf.module.system.workflow.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 自动化规则创建/更新请求。
 *
 * @author AaronZZH & Kiro
 */
public record AutomationRuleCreateDTO(
        @Schema(description = "规则名称") @NotBlank String name,
        @Schema(description = "实体标识") @NotBlank String entitySlug,
        @Schema(description = "触发器类型") @NotBlank String triggerType,
        @Schema(description = "触发条件（JSON）") String conditions,
        @Schema(description = "执行动作（JSON）") @NotBlank String actions,
        @Schema(description = "是否启用") Boolean enabled) {}
