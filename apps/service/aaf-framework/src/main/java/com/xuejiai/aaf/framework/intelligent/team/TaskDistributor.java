/**
 * 任务分发服务。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.framework.intelligent.team;

import java.util.*;

import org.springframework.stereotype.Service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

/** 任务拆解、子任务分配、依赖管理。 将大任务拆解为子任务并分配给团队成员。 */
@Service
@RequiredArgsConstructor
public class TaskDistributor {

    private final TeamOrchestrator orchestrator;

    /** 分配任务给团队成员。 根据成员能力匹配子任务。 */
    public List<TaskAssignment> distribute(String teamId, List<SubTask> subTasks) {
        var members = orchestrator.getMembers(teamId);
        var assignments = new ArrayList<TaskAssignment>();

        for (var task : subTasks) {
            // 找到能力匹配的成员
            var assignee =
                    members.stream()
                            .filter(
                                    m ->
                                            m.getCapabilities() != null
                                                    && m.getCapabilities().stream()
                                                            .anyMatch(
                                                                    c ->
                                                                            task.getRequiredCapability()
                                                                                    .contains(c)))
                            .findFirst()
                            .orElse(members.isEmpty() ? null : members.getFirst());

            if (assignee != null) {
                assignments.add(
                        new TaskAssignment(
                                task.getTaskId(),
                                assignee.getAssistantId(),
                                task,
                                TaskStatus.PENDING));
            }
        }

        return assignments;
    }

    /** 检查依赖是否满足 */
    public boolean areDependenciesMet(SubTask task, Map<String, TaskStatus> statusMap) {
        if (task.getDependencies() == null || task.getDependencies().isEmpty()) {
            return true;
        }
        return task.getDependencies().stream()
                .allMatch(
                        dep ->
                                statusMap.getOrDefault(dep, TaskStatus.PENDING)
                                        == TaskStatus.COMPLETED);
    }

    /** 子任务 */
    @Getter
    @Setter
    public static class SubTask {
        private String taskId;
        private String description;
        private String requiredCapability;
        private List<String> dependencies;
        private int priority;
    }

    /** 任务分配 */
    public record TaskAssignment(
            String taskId, String assigneeId, SubTask task, TaskStatus status) {}

    /** 任务状态 */
    public enum TaskStatus {
        PENDING,
        IN_PROGRESS,
        COMPLETED,
        FAILED,
        CANCELLED
    }
}
