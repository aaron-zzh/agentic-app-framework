package com.xuejiai.aaf.module.company.ops.vo;

import java.time.LocalDateTime;

import com.xuejiai.aaf.module.company.ops.domain.OpsTask;

/** 运营任务出参。 */
public record OpsTaskVO(
        Long id,
        String name,
        String description,
        String category,
        String cronExpr,
        String triggerType,
        Long agentId,
        String config,
        Boolean enabled,
        LocalDateTime createTime,
        LocalDateTime updateTime) {

    public static OpsTaskVO from(OpsTask entity) {
        return new OpsTaskVO(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                entity.getCategory(),
                entity.getCronExpr(),
                entity.getTriggerType(),
                entity.getAgentId(),
                entity.getConfig(),
                entity.getEnabled(),
                entity.getCreateTime(),
                entity.getUpdateTime());
    }
}
