package com.xuejiai.aaf.module.company.ops.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 运营任务创建入参。 */
public record OpsTaskCreateDTO(
        @NotBlank @Size(max = 128) String name,
        @Size(max = 512) String description,
        @NotBlank @Size(max = 32) String category,
        @Size(max = 64) String cronExpr,
        @NotBlank @Size(max = 16) String triggerType,
        Long agentId,
        String config) {}
