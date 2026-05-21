package com.xuejiai.aaf.module.system.workflow.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** 归档规则创建/更新请求。 */
public record ArchiveRuleCreateDTO(
        @NotBlank String entitySlug,
        @NotNull String condition,
        Integer afterDays,
        Boolean enabled) {}
