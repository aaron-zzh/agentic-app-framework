package com.xuejiai.aaf.framework.intelligent.team;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * 目标追踪器——Team 层项目级目标管理。
 *
 * <p>支持目标分解、状态追踪、进度计算。
 */
@Slf4j
@Component
public class GoalTracker {

    /** 目标状态 */
    public enum GoalStatus {
        PENDING, IN_PROGRESS, DONE, FAILED, BLOCKED
    }

    /** 目标定义 */
    public record Goal(
            String id,
            String description,
            GoalStatus status,
            List<String> subGoalIds,
            String assignee,
            Instant createdAt) {
    }

    private final Map<String, Goal> goals = new ConcurrentHashMap<>();

    /** 添加目标 */
    public Goal addGoal(String description, String assignee) {
        var goal = new Goal(
                UUID.randomUUID().toString(),
                description,
                GoalStatus.PENDING,
                new ArrayList<>(),
                assignee,
                Instant.now());
        goals.put(goal.id(), goal);
        log.info("添加目标: id={}, desc={}", goal.id(), description);
        return goal;
    }

    /** 更新目标状态 */
    public void updateStatus(String goalId, GoalStatus status) {
        goals.computeIfPresent(goalId, (k, g) ->
                new Goal(g.id(), g.description(), status, g.subGoalIds(), g.assignee(), g.createdAt()));
    }

    /** 获取目标 */
    public Optional<Goal> getGoal(String goalId) {
        return Optional.ofNullable(goals.get(goalId));
    }

    /** 按状态列出目标 */
    public List<Goal> listByStatus(GoalStatus status) {
        return goals.values().stream()
                .filter(g -> g.status() == status)
                .toList();
    }

    /** 分解目标为子目标 */
    public List<Goal> decompose(String parentGoalId, List<String> subDescriptions) {
        var parent = goals.get(parentGoalId);
        if (parent == null) return List.of();

        var subGoals = subDescriptions.stream()
                .map(desc -> addGoal(desc, parent.assignee()))
                .toList();

        var subIds = new ArrayList<>(parent.subGoalIds());
        subIds.addAll(subGoals.stream().map(Goal::id).toList());
        goals.put(parentGoalId, new Goal(parent.id(), parent.description(),
                GoalStatus.IN_PROGRESS, subIds, parent.assignee(), parent.createdAt()));

        return subGoals;
    }

    /** 计算目标完成进度（百分比） */
    public double getProgress(String goalId) {
        var goal = goals.get(goalId);
        if (goal == null) return 0.0;
        if (goal.subGoalIds().isEmpty()) {
            return goal.status() == GoalStatus.DONE ? 100.0 : 0.0;
        }
        long done = goal.subGoalIds().stream()
                .map(goals::get)
                .filter(g -> g != null && g.status() == GoalStatus.DONE)
                .count();
        return (double) done / goal.subGoalIds().size() * 100.0;
    }
}
