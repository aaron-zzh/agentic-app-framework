package com.xuejiai.aaf.framework.intelligent.team;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * 团队协作规范层——定义协作规则，不直接执行。
 *
 * <p>设计定位：
 * <ul>
 *   <li>Team 是协作规范的容器（谁参与、什么模式、什么规则）</li>
 *   <li>实际执行由 coordinator（协调者 Assistant）通过 A2A 协议驱动</li>
 *   <li>Team 不直接调用 Agent，而是通过 coordinator Assistant 分发任务</li>
 * </ul>
 *
 * <p>协作流程：
 * <pre>
 * 用户请求 → AssistantService（coordinator）→ TeamOrchestrator（查规则）
 *   → coordinator 通过 A2A 分发给成员 Assistant → 汇总结果
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TeamOrchestrator {

    private final Map<String, TeamDefinition> teams = new ConcurrentHashMap<>();

    /** 注册团队协作规范。 */
    public void registerTeam(TeamDefinition team) {
        teams.put(team.getTeamId(), team);
        log.info("注册团队: {} mode={} coordinator={}",
                team.getTeamId(), team.getMode(), team.getCoordinatorAssistantId());
    }

    /** 获取团队定义。 */
    public TeamDefinition getTeam(String teamId) {
        return teams.get(teamId);
    }

    /** 获取协调者 Assistant ID。 */
    public String getCoordinator(String teamId) {
        var team = teams.get(teamId);
        return team != null ? team.getCoordinatorAssistantId() : null;
    }

    /** 获取团队成员列表（不含协调者）。 */
    public List<TeamMember> getMembers(String teamId) {
        var team = teams.get(teamId);
        if (team == null) return List.of();
        return team.getMembers().stream()
                .filter(m -> !m.getAssistantId().equals(team.getCoordinatorAssistantId()))
                .toList();
    }

    /** 获取全部成员（含协调者）。 */
    public List<TeamMember> getAllMembers(String teamId) {
        var team = teams.get(teamId);
        return team != null ? team.getMembers() : List.of();
    }

    /** 团队定义——协作规范 */
    @Getter
    @Setter
    public static class TeamDefinition {
        private String teamId;
        private String name;
        private CollaborationMode mode;
        /** 协调者 Assistant ID（由此 Assistant 驱动协作流程） */
        private String coordinatorAssistantId;
        private List<TeamMember> members;
    }

    /** 团队成员 */
    @Getter
    @Setter
    public static class TeamMember {
        private String assistantId;
        private String role;
        private List<String> capabilities;
    }

    /** 协作模式 */
    public enum CollaborationMode {
        /** 协调者统筹：coordinator 分发任务、汇总结果 */
        COORDINATOR_DRIVEN,
        /** 平等协作：成员间通过 A2A 直接通信，coordinator 仅监控 */
        PEER_COLLABORATION
    }
}
