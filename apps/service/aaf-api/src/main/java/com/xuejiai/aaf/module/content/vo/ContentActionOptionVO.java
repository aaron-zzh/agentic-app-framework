package com.xuejiai.aaf.module.content.vo;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 内容动作选项。
 *
 * @author AaronZZH & Kiro
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ContentActionOptionVO(
        String actionKey,
        String label,
        String targetType,
        Boolean confirmationRequired,
        BigDecimal estimatedCredits,
        List<String> applicableObjectTypes) {}
