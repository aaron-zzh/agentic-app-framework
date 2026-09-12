package com.xuejiai.aaf.framework.intelligent.automation.model;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.xuejiai.aaf.framework.intelligent.assistant.model.CompletionCriteria;
import com.xuejiai.aaf.framework.intelligent.assistant.model.EffectiveContextManifest.SourceReference;
import com.xuejiai.aaf.framework.intelligent.assistant.model.ExecutionContract;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskPlan;
import com.xuejiai.aaf.framework.intelligent.cognition.model.MemoryRecord.MemorySubject;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.AssistantId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TaskId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.UserId;

/** 从已验收 P4 委托任务提炼的不可变自动化版本。 */
public record AutomationDefinition(
        TenantId tenantId,
        String automationId,
        String name,
        long version,
        TaskId sourceTaskId,
        TaskTemplate template,
        ParameterSchema parameterSchema,
        TriggerSpec trigger,
        PermissionSnapshot permissionSnapshot,
        FailurePolicy failurePolicy,
        NotificationPlan notificationPlan,
        Lifecycle lifecycle,
        boolean enabled,
        boolean previewPassed,
        boolean dryRunPassed,
        Instant createdAt,
        Instant updatedAt) {

    public AutomationDefinition {
        Objects.requireNonNull(tenantId, "tenantId 不能为空");
        automationId = text(automationId, "automationId");
        name = text(name, "name");
        if (version < 1) throw new IllegalArgumentException("version 必须大于 0");
        Objects.requireNonNull(sourceTaskId, "sourceTaskId 不能为空");
        Objects.requireNonNull(template, "template 不能为空");
        Objects.requireNonNull(parameterSchema, "parameterSchema 不能为空");
        Objects.requireNonNull(trigger, "trigger 不能为空");
        Objects.requireNonNull(permissionSnapshot, "permissionSnapshot 不能为空");
        Objects.requireNonNull(failurePolicy, "failurePolicy 不能为空");
        Objects.requireNonNull(notificationPlan, "notificationPlan 不能为空");
        Objects.requireNonNull(lifecycle, "lifecycle 不能为空");
        Objects.requireNonNull(createdAt, "createdAt 不能为空");
        Objects.requireNonNull(updatedAt, "updatedAt 不能为空");
        if (enabled && (lifecycle != Lifecycle.PUBLISHED || !previewPassed || !dryRunPassed)) {
            throw new IllegalArgumentException("启用前必须发布并通过 preview 与 dry-run");
        }
    }

    public AutomationDefinition assessed(boolean preview, boolean dryRun, Instant at) {
        return copy(lifecycle, false, preview, dryRun, at);
    }

    public AutomationDefinition publish(Instant at) {
        if (!previewPassed || !dryRunPassed)
            throw new IllegalStateException("发布前必须通过 preview 与 dry-run");
        return copy(Lifecycle.PUBLISHED, false, true, true, at);
    }

    public AutomationDefinition enable(Instant at) {
        if (lifecycle != Lifecycle.PUBLISHED || !previewPassed || !dryRunPassed) {
            throw new IllegalStateException("自动化尚未满足启用条件");
        }
        return copy(lifecycle, true, true, true, at);
    }

    public AutomationDefinition disable(Instant at) {
        return copy(Lifecycle.DISABLED, false, previewPassed, dryRunPassed, at);
    }

    private AutomationDefinition copy(
            Lifecycle nextLifecycle,
            boolean nextEnabled,
            boolean preview,
            boolean dryRun,
            Instant at) {
        return new AutomationDefinition(
                tenantId,
                automationId,
                name,
                version,
                sourceTaskId,
                template,
                parameterSchema,
                trigger,
                permissionSnapshot,
                failurePolicy,
                notificationPlan,
                nextLifecycle,
                nextEnabled,
                preview,
                dryRun,
                createdAt,
                at);
    }

    public record TaskTemplate(
            UserId ownerId,
            AssistantId assistantId,
            MemorySubject memorySubject,
            String goal,
            CompletionCriteria completionCriteria,
            List<SourceReference> contextCandidates,
            TaskPlan board,
            ExecutionContract contract,
            Duration executionWindow) {
        public TaskTemplate {
            Objects.requireNonNull(ownerId, "ownerId 不能为空");
            Objects.requireNonNull(assistantId, "assistantId 不能为空");
            Objects.requireNonNull(memorySubject, "memorySubject 不能为空");
            goal = text(goal, "goal");
            Objects.requireNonNull(completionCriteria, "completionCriteria 不能为空");
            contextCandidates =
                    List.copyOf(
                            Objects.requireNonNull(contextCandidates, "contextCandidates 不能为空"));
            Objects.requireNonNull(board, "board 不能为空");
            Objects.requireNonNull(contract, "contract 不能为空");
            Objects.requireNonNull(executionWindow, "executionWindow 不能为空");
            if (executionWindow.isNegative() || executionWindow.isZero())
                throw new IllegalArgumentException("executionWindow 必须大于 0");
        }
    }

    public record ParameterSchema(Map<String, String> properties, Set<String> required) {
        public ParameterSchema {
            properties = Map.copyOf(Objects.requireNonNull(properties, "properties 不能为空"));
            required = Set.copyOf(Objects.requireNonNull(required, "required 不能为空"));
            if (!properties.keySet().containsAll(required))
                throw new IllegalArgumentException("required 必须属于 properties");
        }

        public void validate(Map<String, Object> parameters) {
            var values = parameters == null ? Map.<String, Object>of() : parameters;
            var missing = required.stream().filter(key -> !values.containsKey(key)).toList();
            if (!missing.isEmpty()) throw new IllegalArgumentException("缺少自动化参数: " + missing);
            var unknown =
                    values.keySet().stream().filter(key -> !properties.containsKey(key)).toList();
            if (!unknown.isEmpty()) throw new IllegalArgumentException("未知自动化参数: " + unknown);
        }
    }

    public record TriggerSpec(TriggerType type, String cron, Duration frequency) {
        public TriggerSpec {
            Objects.requireNonNull(type, "trigger type 不能为空");
            if (type == TriggerType.CRON && (cron == null || cron.isBlank()))
                throw new IllegalArgumentException("CRON 必须声明 cron");
            if (type == TriggerType.FREQUENCY
                    && (frequency == null || frequency.compareTo(Duration.ofSeconds(1)) < 0)) {
                throw new IllegalArgumentException("FREQUENCY 必须声明至少一秒的正周期");
            }
        }
    }

    public record PermissionSnapshot(
            Set<String> actions, String policyVersion, Instant capturedAt) {
        public PermissionSnapshot {
            actions = Set.copyOf(Objects.requireNonNull(actions, "actions 不能为空"));
            policyVersion = text(policyVersion, "policyVersion");
            Objects.requireNonNull(capturedAt, "capturedAt 不能为空");
            if (actions.isEmpty()) throw new IllegalArgumentException("权限快照不能为空");
        }
    }

    public record FailurePolicy(int maxAttempts, Duration backoff, FailureAction exhaustedAction) {
        public FailurePolicy {
            Objects.requireNonNull(backoff, "backoff 不能为空");
            Objects.requireNonNull(exhaustedAction, "exhaustedAction 不能为空");
            if (maxAttempts < 1 || backoff.isZero() || backoff.isNegative())
                throw new IllegalArgumentException("失败策略无效");
        }
    }

    public record NotificationPlan(Set<String> channels, Set<String> events) {
        public NotificationPlan {
            channels = Set.copyOf(Objects.requireNonNull(channels, "channels 不能为空"));
            events = Set.copyOf(Objects.requireNonNull(events, "events 不能为空"));
            if (channels.isEmpty() || events.isEmpty())
                throw new IllegalArgumentException("通知渠道与事件不能为空");
        }
    }

    public enum TriggerType {
        MANUAL,
        CRON,
        FREQUENCY
    }

    public enum FailureAction {
        PAUSE,
        DISABLE,
        NOTIFY_OWNER
    }

    public enum Lifecycle {
        DRAFT,
        PUBLISHED,
        DEPRECATED,
        DISABLED
    }

    private static String text(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " 不能为空白");
        return value;
    }
}
