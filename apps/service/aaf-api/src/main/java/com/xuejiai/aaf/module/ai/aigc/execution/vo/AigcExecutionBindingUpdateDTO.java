package com.xuejiai.aaf.module.ai.aigc.execution.vo;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record AigcExecutionBindingUpdateDTO(
        String actionKey,
        String projectTypeCode,
        String domainExtensionCode,
        String productionMode,
        String channelCode,
        String targetType,
        String targetRef,
        String bindingVersion,
        Integer priority,
        Boolean confirmationRequired,
        BigDecimal estimatedCredits,
        @NotNull @PositiveOrZero Integer expectedVersion) {}
