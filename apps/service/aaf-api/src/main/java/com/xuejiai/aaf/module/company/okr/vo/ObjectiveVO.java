package com.xuejiai.aaf.module.company.okr.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.xuejiai.aaf.module.company.okr.domain.Objective;

/** OKR 目标出参。 */
public record ObjectiveVO(
        Long id,
        String title,
        Long planId,
        Long parentId,
        Long ownerUserId,
        BigDecimal progress,
        String status,
        String period,
        LocalDateTime createTime,
        LocalDateTime updateTime) {

    public static ObjectiveVO from(Objective entity) {
        return new ObjectiveVO(
                entity.getId(),
                entity.getTitle(),
                entity.getPlanId(),
                entity.getParentId(),
                entity.getOwnerUserId(),
                entity.getProgress(),
                entity.getStatus(),
                entity.getPeriod(),
                entity.getCreateTime(),
                entity.getUpdateTime());
    }
}
