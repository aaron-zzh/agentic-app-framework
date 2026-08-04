package com.xuejiai.aaf.module.ai.aigc.execution.vo;

import java.math.BigDecimal;
import java.util.List;

public record AigcActionOptionVO(
        String actionKey,
        String label,
        String targetType,
        Boolean confirmationRequired,
        BigDecimal estimatedCredits,
        List<String> applicableObjectTypes) {}
