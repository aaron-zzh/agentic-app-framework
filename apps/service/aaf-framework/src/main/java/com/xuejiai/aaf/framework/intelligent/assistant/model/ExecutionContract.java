package com.xuejiai.aaf.framework.intelligent.assistant.model;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/** 用户授予 Assistant 的有界委托合同。 */
public record ExecutionContract(
        BudgetLimit budget,
        Instant deadline,
        int maxModelCalls,
        int maxToolCalls,
        Set<String> allowedActions,
        Set<StopCondition> stopConditions,
        RetryPolicy retryPolicy,
        NotificationPolicy notificationPolicy,
        ResponsibleOwner responsibleOwner,
        TakeoverPolicy takeoverPolicy) {

    public ExecutionContract {
        Objects.requireNonNull(budget, "budget 不能为空");
        Objects.requireNonNull(deadline, "deadline 不能为空");
        allowedActions = Set.copyOf(Objects.requireNonNull(allowedActions, "allowedActions 不能为空"));
        stopConditions = Set.copyOf(Objects.requireNonNull(stopConditions, "stopConditions 不能为空"));
        Objects.requireNonNull(retryPolicy, "retryPolicy 不能为空");
        Objects.requireNonNull(notificationPolicy, "notificationPolicy 不能为空");
        Objects.requireNonNull(responsibleOwner, "responsibleOwner 不能为空");
        Objects.requireNonNull(takeoverPolicy, "takeoverPolicy 不能为空");
        if (maxModelCalls < 1 || maxToolCalls < 1) {
            throw new IllegalArgumentException("最大模型和工具调用次数必须大于 0");
        }
        if (allowedActions.isEmpty() || allowedActions.stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException("委托合同必须声明至少一个允许动作");
        }
        if (stopConditions.isEmpty()) {
            throw new IllegalArgumentException("委托合同必须声明停止条件");
        }
        var mandatoryStops = Set.of(
                StopCondition.COMPLETED,
                StopCondition.DEADLINE_REACHED,
                StopCondition.BUDGET_EXHAUSTED,
                StopCondition.AUTHORIZATION_MISSING,
                StopCondition.CONSECUTIVE_FAILURES,
                StopCondition.HUMAN_TAKEOVER,
                StopCondition.USER_CANCELED);
        if (!stopConditions.containsAll(mandatoryStops)) {
            var effectiveStopConditions = stopConditions;
            throw new IllegalArgumentException("委托合同缺少强制停止条件: "
                    + mandatoryStops.stream()
                            .filter(value -> !effectiveStopConditions.contains(value))
                            .toList());
        }
        if (!takeoverPolicy.humanTakeoverAllowed()) {
            throw new IllegalArgumentException("DELEGATED 必须允许人工接管");
        }
    }

    /**
     * 对话内任务的安全默认档，由系统托底预算、停止条件和接管策略，用户无需逐项填写。
     */
    public static ExecutionContract conversationDefault(
            Set<String> allowedActions, ResponsibleOwner responsibleOwner) {
        return new ExecutionContract(
                new BudgetLimit(1_000_000, 1_000, new BigDecimal("100")),
                Instant.now().plus(Duration.ofHours(24)),
                200,
                200,
                allowedActions,
                Set.of(
                        StopCondition.COMPLETED,
                        StopCondition.DEADLINE_REACHED,
                        StopCondition.BUDGET_EXHAUSTED,
                        StopCondition.AUTHORIZATION_MISSING,
                        StopCondition.CONSECUTIVE_FAILURES,
                        StopCondition.HUMAN_TAKEOVER,
                        StopCondition.USER_CANCELED),
                new RetryPolicy(3, Duration.ofSeconds(30), true),
                new NotificationPolicy(
                        Set.of(
                                NotificationTrigger.COMPLETED,
                                NotificationTrigger.CONSECUTIVE_FAILURE),
                        0.8),
                responsibleOwner,
                new TakeoverPolicy(true, true, false));
    }

    public void requireUsableAt(Instant at) {
        Objects.requireNonNull(at, "检查时间不能为空");
        if (!deadline.isAfter(at)) {
            throw new IllegalStateException("委托合同已到 deadline");
        }
    }

    public void requireAction(String action) {
        if (action == null || action.isBlank() || !allowedActions.contains(action)) {
            throw new IllegalStateException("委托合同未允许动作: " + action);
        }
    }

    public record BudgetLimit(long modelTokens, long toolUnits, BigDecimal credits) {
        public BudgetLimit {
            Objects.requireNonNull(credits, "积分预算不能为空");
            if (modelTokens < 1 || toolUnits < 1 || credits.signum() <= 0) {
                throw new IllegalArgumentException("模型、工具和积分预算必须大于 0");
            }
        }
    }

    public record RetryPolicy(int maxAttempts, Duration initialBackoff, boolean retryTransientOnly) {
        public RetryPolicy {
            Objects.requireNonNull(initialBackoff, "initialBackoff 不能为空");
            if (maxAttempts < 1 || initialBackoff.isZero() || initialBackoff.isNegative()) {
                throw new IllegalArgumentException("重试次数和退避时间必须大于 0");
            }
        }
    }

    public record NotificationPolicy(Set<NotificationTrigger> triggers, double budgetWarningRatio) {
        public NotificationPolicy {
            triggers = Set.copyOf(Objects.requireNonNull(triggers, "通知触发器不能为空"));
            if (triggers.isEmpty()) {
                throw new IllegalArgumentException("委托合同必须声明通知策略");
            }
            if (budgetWarningRatio <= 0 || budgetWarningRatio >= 1) {
                throw new IllegalArgumentException("预算临界通知比例必须在 0 和 1 之间");
            }
        }
    }

    public record ResponsibleOwner(String ownerType, String ownerId) {
        public ResponsibleOwner {
            if (ownerType == null || ownerType.isBlank() || ownerId == null || ownerId.isBlank()) {
                throw new IllegalArgumentException("负责人类型和标识不能为空白");
            }
        }
    }

    public record TakeoverPolicy(
            boolean humanTakeoverAllowed,
            boolean handBackAllowed,
            boolean reasonRequired) {}

    public enum StopCondition {
        COMPLETED,
        DEADLINE_REACHED,
        BUDGET_EXHAUSTED,
        AUTHORIZATION_MISSING,
        CONSECUTIVE_FAILURES,
        HUMAN_TAKEOVER,
        USER_CANCELED
    }

    public enum NotificationTrigger {
        STATUS_CHANGED,
        AUTHORIZATION_GAP,
        CONSECUTIVE_FAILURE,
        BUDGET_WARNING,
        COMPLETED
    }
}
