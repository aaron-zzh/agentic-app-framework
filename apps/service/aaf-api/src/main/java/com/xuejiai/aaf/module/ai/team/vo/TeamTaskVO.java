package com.xuejiai.aaf.module.ai.team.vo;

import java.time.LocalDateTime;

import com.xuejiai.aaf.framework.intelligent.team.TeamTaskEntity;

/** 智能体团队子任务响应 VO。 */
public record TeamTaskVO(
        Long id,
        Long teamId,
        String taskId,
        String parentTaskId,
        Long assigneeId,
        String description,
        String requiredCapability,
        String status,
        String dependencies,
        Integer priority,
        Integer progress,
        String result,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static TeamTaskVO from(TeamTaskEntity entity) {
        return new TeamTaskVO(
                entity.getId(),
                entity.getTeamId(),
                entity.getTaskId(),
                entity.getParentTaskId(),
                entity.getAssigneeId(),
                entity.getDescription(),
                entity.getRequiredCapability(),
                entity.getStatus(),
                entity.getDependencies(),
                entity.getPriority(),
                entity.getProgress(),
                entity.getResult(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
