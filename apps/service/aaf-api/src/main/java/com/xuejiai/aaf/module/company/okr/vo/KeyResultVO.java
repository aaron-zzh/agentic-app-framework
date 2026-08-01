package com.xuejiai.aaf.module.company.okr.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.xuejiai.aaf.module.company.okr.domain.KeyResult;

/** 关键结果出参。 */
public record KeyResultVO(
        Long id,
        Long objectiveId,
        String title,
        String metricType,
        BigDecimal startValue,
        BigDecimal targetValue,
        BigDecimal currentValue,
        Long ownerUserId,
        String status,
        LocalDateTime createTime,
        LocalDateTime updateTime) {

    public static KeyResultVO from(KeyResult entity) {
        return new KeyResultVO(
                entity.getId(),
                entity.getObjectiveId(),
                entity.getTitle(),
                entity.getMetricType(),
                entity.getStartValue(),
                entity.getTargetValue(),
                entity.getCurrentValue(),
                entity.getOwnerUserId(),
                entity.getStatus(),
                entity.getCreateTime(),
                entity.getUpdateTime());
    }
}
