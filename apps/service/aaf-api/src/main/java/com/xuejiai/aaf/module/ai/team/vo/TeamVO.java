package com.xuejiai.aaf.module.ai.team.vo;

import java.time.LocalDateTime;

import com.xuejiai.aaf.framework.intelligent.team.TeamEntity;

/** 智能体团队响应 VO。 */
public record TeamVO(
        Long id,
        String name,
        String description,
        String collaborationMode,
        Long coordinatorAssistantId,
        String status,
        LocalDateTime createTime,
        LocalDateTime updateTime) {

    public static TeamVO from(TeamEntity entity) {
        return new TeamVO(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                entity.getCollaborationMode(),
                entity.getCoordinatorAssistantId(),
                entity.getStatus(),
                entity.getCreateTime(),
                entity.getUpdateTime());
    }
}
