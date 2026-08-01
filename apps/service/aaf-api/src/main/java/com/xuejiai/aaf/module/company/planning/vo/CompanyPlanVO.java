package com.xuejiai.aaf.module.company.planning.vo;

import java.time.LocalDateTime;

import com.xuejiai.aaf.module.company.planning.domain.CompanyPlan;

/** 企业规划出参。 */
public record CompanyPlanVO(
        Long id,
        String name,
        String planType,
        String period,
        Integer year,
        Integer quarter,
        String content,
        String status,
        LocalDateTime createTime,
        LocalDateTime updateTime) {

    public static CompanyPlanVO from(CompanyPlan entity) {
        return new CompanyPlanVO(
                entity.getId(),
                entity.getName(),
                entity.getPlanType(),
                entity.getPeriod(),
                entity.getYear(),
                entity.getQuarter(),
                entity.getContent(),
                entity.getStatus(),
                entity.getCreateTime(),
                entity.getUpdateTime());
    }
}
