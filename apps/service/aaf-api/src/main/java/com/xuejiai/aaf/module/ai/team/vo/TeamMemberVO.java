package com.xuejiai.aaf.module.ai.team.vo;

import java.time.LocalDateTime;

import com.xuejiai.aaf.framework.intelligent.team.TeamMemberEntity;

/** 智能体团队成员响应 VO。 */
public record TeamMemberVO(
        Long id,
        Long teamId,
        Long assistantId,
        String role,
        String capabilities,
        LocalDateTime createdAt) {

    public static TeamMemberVO from(TeamMemberEntity entity) {
        return new TeamMemberVO(
                entity.getId(),
                entity.getTeamId(),
                entity.getAssistantId(),
                entity.getRole(),
                entity.getCapabilities(),
                entity.getCreatedAt());
    }
}
