package com.xuejiai.aaf.module.brokerage.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 会员等级佣金加成响应 VO。 */
public record BrokerageLevelBonusVO(
        Long id,
        Long ruleId,
        Long planId,
        BigDecimal level1Rate,
        BigDecimal level2Rate,
        LocalDateTime createTime,
        LocalDateTime updateTime) {}
