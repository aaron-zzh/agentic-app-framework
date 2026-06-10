/**
 * 任务分发服务。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.framework.intelligent.team;

import java.util.*;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/** 任务拆解、子任务分配、依赖管理。 */
@Service
@RequiredArgsConstructor
public class TaskDistributor {

    private final TeamOrchestrator orchestrator;

    /** 分配任务给团队成员（按能力匹配）。 */
    public List<TaskAssignment> distribute(Long teamId, List<TeamTaskEntity> tasks) {
        var members = orchestrator.getMembers(teamId);
        var assignments = new ArrayList<TaskAssignment>();

        for (var task : tasks) {
            var assignee =
                    members.stream()
                            .filter(
                                    m ->
                                            m.getCapabilities() != null
                                                    && task.getRequiredCapability() != null
                                                    && m.getCapabilities()
                                                            .contains(task.getRequiredCapability()))
                            .findFirst()
                            .orElse(members.isEmpty() ? null : members.getFirst());

            if (assignee != null) {
                task.setAssigneeId(assignee.getAssistantId());
                assignments.add(
                        new TaskAssignment(
                                task.getTaskId(), assignee.getAssistantId().toString(), task));
            }
        }
        return assignments;
    }

    /** 任务分配 */
    public record TaskAssignment(String taskId, String assigneeId, TeamTaskEntity task) {}
}
