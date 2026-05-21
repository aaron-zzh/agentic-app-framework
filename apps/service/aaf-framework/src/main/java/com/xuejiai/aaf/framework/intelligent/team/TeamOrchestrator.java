/**
 * 团队编排服务。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.framework.intelligent.team;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

/** 多 Assistant 编排：团队定义、角色分配、协作模式。 支持 Leader 协调和平等协作两种模式。 */
@Service
@RequiredArgsConstructor
public class TeamOrchestrator {

    private final Map<String, TeamDefinition> teams = new ConcurrentHashMap<>();

    /** 注册团队 */
    public void registerTeam(TeamDefinition team) {
        teams.put(team.getTeamId(), team);
    }

    /** 获取团队 */
    public TeamDefinition getTeam(String teamId) {
        return teams.get(teamId);
    }

    /** 获取团队中的 Leader */
    public String getLeader(String teamId) {
        var team = teams.get(teamId);
        if (team == null) return null;
        return team.getMembers().stream()
                .filter(m -> "leader".equals(m.getRole()))
                .map(TeamMember::getAssistantId)
                .findFirst()
                .orElse(null);
    }

    /** 获取团队成员列表 */
    public List<TeamMember> getMembers(String teamId) {
        var team = teams.get(teamId);
        return team != null ? team.getMembers() : List.of();
    }

    /** 团队定义 */
    @Getter
    @Setter
    public static class TeamDefinition {
        private String teamId;
        private String name;
        private CollaborationMode mode;
        private List<TeamMember> members;
    }

    /** 团队成员 */
    @Getter
    @Setter
    public static class TeamMember {
        private String assistantId;
        private String role; // leader / member
        private List<String> capabilities;
    }

    /** 协作模式 */
    public enum CollaborationMode {
        LEADER_COORDINATED, // Leader 统筹
        PEER_COLLABORATION // 平等协作
    }
}
