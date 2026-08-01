package com.xuejiai.aaf.module.company.ops.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.xuejiai.aaf.module.company.ops.domain.OpsMetric;

/** 运营指标出参。 */
public record OpsMetricVO(
        Long id,
        String name,
        String code,
        BigDecimal value,
        String unit,
        LocalDateTime recordedAt,
        String source,
        LocalDateTime createTime,
        LocalDateTime updateTime) {

    public static OpsMetricVO from(OpsMetric entity) {
        return new OpsMetricVO(
                entity.getId(),
                entity.getName(),
                entity.getCode(),
                entity.getValue(),
                entity.getUnit(),
                entity.getRecordedAt(),
                entity.getSource(),
                entity.getCreateTime(),
                entity.getUpdateTime());
    }
}
