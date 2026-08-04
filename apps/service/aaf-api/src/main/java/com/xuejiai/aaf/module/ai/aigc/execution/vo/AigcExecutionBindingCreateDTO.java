package com.xuejiai.aaf.module.ai.aigc.execution.vo;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;

public record AigcExecutionBindingCreateDTO(
        @NotBlank String actionKey,
        String projectTypeCode,
        String domainExtensionCode,
        String productionMode,
        String channelCode,
        @NotBlank String targetType,
        @NotBlank String targetRef,
        @NotBlank String bindingVersion,
        Integer priority,
        Boolean confirmationRequired,
        BigDecimal estimatedCredits) {}
