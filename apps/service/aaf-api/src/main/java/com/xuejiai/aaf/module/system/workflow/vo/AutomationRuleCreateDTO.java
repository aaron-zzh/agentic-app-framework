package com.xuejiai.aaf.module.system.workflow.vo;

import jakarta.validation.constraints.NotBlank;

/** 自动化规则创建/更新请求。 */
public record AutomationRuleCreateDTO(
        @NotBlank String name,
        @NotBlank String entitySlug,
        @NotBlank String triggerType,
        String conditions,
        @NotBlank String actions,
        Boolean enabled) {}
