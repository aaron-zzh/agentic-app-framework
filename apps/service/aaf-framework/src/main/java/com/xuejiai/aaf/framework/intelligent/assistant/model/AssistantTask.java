package com.xuejiai.aaf.framework.intelligent.assistant.model;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.ControlMode;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TaskId;

/** Assistant 持有的稳定任务生命周期，独立于模型回合和聊天消息。 */
public record AssistantTask(
        TaskId taskId,
        TaskStatus status,
        ControlMode controlMode,
        TaskOwner owner,
        RecoveryPoint recoveryPoint,
        List<TaskTransition> transitions) {

    private static final Map<TaskStatus, Set<TaskStatus>> ALLOWED_TRANSITIONS = buildTransitions();

    public AssistantTask {
        Objects.requireNonNull(taskId, "taskId 不能为空");
        Objects.requireNonNull(status, "status 不能为空");
        Objects.requireNonNull(controlMode, "controlMode 不能为空");
        Objects.requireNonNull(owner, "owner 不能为空");
        transitions = List.copyOf(Objects.requireNonNull(transitions, "transitions 不能为空"));
    }

    public static AssistantTask draft(
            TaskId taskId,
            ControlMode controlMode,
            TaskOwner owner,
            String reason,
            TaskActor initiatedBy,
            Instant at) {
        var transition =
                new TaskTransition(
                        null,
                        TaskStatus.DRAFT,
                        requireReason(reason),
                        initiatedBy,
                        at,
                        owner,
                        null);
        return new AssistantTask(
                taskId, TaskStatus.DRAFT, controlMode, owner, null, List.of(transition));
    }

    public AssistantTask transitionTo(
            TaskStatus next,
            String reason,
            TaskActor initiatedBy,
            TaskOwner nextOwner,
            RecoveryPoint nextRecoveryPoint,
            Instant at) {
        Objects.requireNonNull(next, "next status 不能为空");
        Objects.requireNonNull(initiatedBy, "initiatedBy 不能为空");
        Objects.requireNonNull(nextOwner, "nextOwner 不能为空");
        Objects.requireNonNull(at, "transition time 不能为空");
        if (next == status) {
            throw new IllegalStateException("任务不能重复转换到当前状态: " + status);
        }
        if (!ALLOWED_TRANSITIONS.getOrDefault(status, Set.of()).contains(next)) {
            throw new IllegalStateException("非法任务状态转换: " + status + " -> " + next);
        }
        var transition =
                new TaskTransition(
                        status,
                        next,
                        requireReason(reason),
                        initiatedBy,
                        at,
                        nextOwner,
                        nextRecoveryPoint);
        var history = new java.util.ArrayList<>(transitions);
        history.add(transition);
        return new AssistantTask(
                taskId,
                next,
                controlMode,
                nextOwner,
                nextRecoveryPoint,
                history);
    }

    public AssistantTask changeControlMode(
            ControlMode next, String reason, TaskActor initiatedBy, Instant at) {
        Objects.requireNonNull(next, "next controlMode 不能为空");
        if (next != ControlMode.READ_ONLY && next != ControlMode.COLLABORATIVE) {
            throw new IllegalArgumentException("P2 仅支持 READ_ONLY 和 COLLABORATIVE");
        }
        if (next == controlMode) {
            return this;
        }
        var marker =
                new TaskTransition(
                        status,
                        status,
                        requireReason(reason),
                        initiatedBy,
                        at,
                        owner,
                        recoveryPoint);
        var history = new java.util.ArrayList<>(transitions);
        history.add(marker);
        return new AssistantTask(taskId, status, next, owner, recoveryPoint, history);
    }

    private static String requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("状态转换理由不能为空白");
        }
        return reason;
    }

    private static Map<TaskStatus, Set<TaskStatus>> buildTransitions() {
        var allowed = new EnumMap<TaskStatus, Set<TaskStatus>>(TaskStatus.class);
        allowed.put(
                TaskStatus.DRAFT,
                Set.of(TaskStatus.PLANNING, TaskStatus.CANCELED, TaskStatus.FAILED));
        allowed.put(
                TaskStatus.PLANNING,
                Set.of(
                        TaskStatus.AWAITING_AUTHORIZATION,
                        TaskStatus.AWAITING_INPUT,
                        TaskStatus.RUNNING,
                        TaskStatus.PAUSED,
                        TaskStatus.CANCELED,
                        TaskStatus.FAILED));
        allowed.put(
                TaskStatus.AWAITING_AUTHORIZATION,
                Set.of(
                        TaskStatus.RUNNING,
                        TaskStatus.AWAITING_INPUT,
                        TaskStatus.PAUSED,
                        TaskStatus.CANCELED,
                        TaskStatus.FAILED));
        allowed.put(
                TaskStatus.RUNNING,
                Set.of(
                        TaskStatus.VERIFYING,
                        TaskStatus.AWAITING_AUTHORIZATION,
                        TaskStatus.AWAITING_INPUT,
                        TaskStatus.PAUSED,
                        TaskStatus.CANCELED,
                        TaskStatus.FAILED));
        allowed.put(
                TaskStatus.VERIFYING,
                Set.of(
                        TaskStatus.COMPLETED,
                        TaskStatus.RUNNING,
                        TaskStatus.AWAITING_INPUT,
                        TaskStatus.PAUSED,
                        TaskStatus.CANCELED,
                        TaskStatus.FAILED));
        allowed.put(
                TaskStatus.AWAITING_INPUT,
                Set.of(
                        TaskStatus.PLANNING,
                        TaskStatus.RUNNING,
                        TaskStatus.PAUSED,
                        TaskStatus.CANCELED,
                        TaskStatus.FAILED));
        allowed.put(
                TaskStatus.PAUSED,
                Set.of(
                        TaskStatus.PLANNING,
                        TaskStatus.RUNNING,
                        TaskStatus.RECOVERING,
                        TaskStatus.CANCELED,
                        TaskStatus.FAILED));
        allowed.put(TaskStatus.FAILED, Set.of(TaskStatus.RECOVERING));
        allowed.put(
                TaskStatus.RECOVERING,
                Set.of(
                        TaskStatus.PLANNING,
                        TaskStatus.RUNNING,
                        TaskStatus.AWAITING_INPUT,
                        TaskStatus.CANCELED,
                        TaskStatus.FAILED));
        allowed.put(TaskStatus.COMPLETED, Set.of());
        allowed.put(TaskStatus.CANCELED, Set.of());
        return Map.copyOf(allowed);
    }

    public enum TaskStatus {
        DRAFT,
        PLANNING,
        AWAITING_AUTHORIZATION,
        RUNNING,
        VERIFYING,
        COMPLETED,
        AWAITING_INPUT,
        PAUSED,
        CANCELED,
        FAILED,
        RECOVERING
    }

    public record TaskOwner(OwnerKind kind, String ownerId) {
        public TaskOwner {
            Objects.requireNonNull(kind, "owner kind 不能为空");
            if (ownerId == null || ownerId.isBlank()) {
                throw new IllegalArgumentException("ownerId 不能为空白");
            }
        }
    }

    public record TaskActor(OwnerKind kind, String actorId) {
        public TaskActor {
            Objects.requireNonNull(kind, "actor kind 不能为空");
            if (actorId == null || actorId.isBlank()) {
                throw new IllegalArgumentException("actorId 不能为空白");
            }
        }
    }

    public enum OwnerKind {
        SYSTEM,
        HUMAN,
        ASSISTANT,
        AGENT
    }

    public record RecoveryPoint(String key, String description) {
        public RecoveryPoint {
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("recovery key 不能为空白");
            }
            if (description == null || description.isBlank()) {
                throw new IllegalArgumentException("recovery description 不能为空白");
            }
        }
    }

    public record TaskTransition(
            TaskStatus from,
            TaskStatus to,
            String reason,
            TaskActor initiatedBy,
            Instant at,
            TaskOwner owner,
            RecoveryPoint recoveryPoint) {

        public TaskTransition {
            Objects.requireNonNull(to, "transition to 不能为空");
            requireReason(reason);
            Objects.requireNonNull(initiatedBy, "initiatedBy 不能为空");
            Objects.requireNonNull(at, "transition at 不能为空");
            Objects.requireNonNull(owner, "transition owner 不能为空");
        }
    }
}
