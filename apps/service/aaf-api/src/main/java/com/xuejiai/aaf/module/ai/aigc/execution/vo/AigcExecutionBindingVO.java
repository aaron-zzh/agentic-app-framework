package com.xuejiai.aaf.module.ai.aigc.execution.vo;

import java.math.BigDecimal;

public record AigcExecutionBindingVO(
        Long id,
        Integer version,
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
        String status) {}
