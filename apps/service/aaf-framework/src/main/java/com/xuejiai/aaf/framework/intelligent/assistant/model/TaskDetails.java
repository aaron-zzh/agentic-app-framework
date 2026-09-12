package com.xuejiai.aaf.framework.intelligent.assistant.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** 面向 API/SSE 的 canonical Task 只读投影；不暴露 tenant/user、合同、checkpoint、lease、fence 或内部版本。 */
public record TaskDetails(
        String taskId,
        String conversationId,
        String originExecutionId,
        String source,
        int priority,
        String status,
        String controlMode,
        String ownerKind,
        String goal,
        PlanDetails plan,
        RootResultDetails rootResult,
        List<ExecutionDetails> executions,
        Instant createdAt,
        Instant updatedAt) {

    public TaskDetails {
        taskId = requireText(taskId, "taskId");
        conversationId = requireText(conversationId, "conversationId");
        source = requireText(source, "source");
        status = requireText(status, "status");
        controlMode = requireText(controlMode, "controlMode");
        ownerKind = requireText(ownerKind, "ownerKind");
        goal = normalize(goal);
        if (priority < 0) {
            throw new IllegalArgumentException("priority 不能为负数");
        }
        executions = List.copyOf(Objects.requireNonNull(executions, "executions 不能为空"));
        Objects.requireNonNull(createdAt, "createdAt 不能为空");
        Objects.requireNonNull(updatedAt, "updatedAt 不能为空");
    }

    public record RootResultDetails(
            String result, int planRevision, String sourceNodeId, Instant committedAt) {
        public RootResultDetails {
            result = Objects.requireNonNull(result, "root result 不能为空");
            sourceNodeId = requireText(sourceNodeId, "root sourceNodeId");
            if (planRevision < 1) {
                throw new IllegalArgumentException("root planRevision 必须为正数");
            }
            Objects.requireNonNull(committedAt, "root committedAt 不能为空");
        }
    }

    public record PlanDetails(
            String planId,
            int revision,
            String status,
            int maxParallelism,
            String failurePolicy,
            String aggregationKind,
            List<NodeDetails> nodes) {
        public PlanDetails {
            planId = requireText(planId, "planId");
            status = requireText(status, "plan status");
            failurePolicy = requireText(failurePolicy, "failurePolicy");
            aggregationKind = requireText(aggregationKind, "aggregationKind");
            if (revision < 1 || maxParallelism < 1) {
                throw new IllegalArgumentException("plan revision/maxParallelism 必须为正数");
            }
            nodes = List.copyOf(Objects.requireNonNull(nodes, "nodes 不能为空"));
        }
    }

    public record NodeDetails(
            String nodeId,
            String kind,
            String description,
            List<String> dependsOn,
            String status,
            int attempts,
            int maxAttempts,
            String result,
            String failure,
            ExecutorPlanDetails executorPlan) {
        public NodeDetails {
            nodeId = requireText(nodeId, "nodeId");
            kind = requireText(kind, "node kind");
            description = requireText(description, "node description");
            status = requireText(status, "node status");
            dependsOn = List.copyOf(Objects.requireNonNull(dependsOn, "dependsOn 不能为空"));
            if (attempts < 0 || maxAttempts < 1 || attempts > maxAttempts) {
                throw new IllegalArgumentException("node attempts 不合法");
            }
        }
    }

    /** 节点内最新 ExecutorPlan 的用户安全投影；不暴露 instruction、工具、策略、审批身份或内部版本。 */
    public record ExecutorPlanDetails(
            int revision, String status, String goal, List<ExecutorPlanStepDetails> steps) {
        public ExecutorPlanDetails {
            if (revision < 1) {
                throw new IllegalArgumentException("executor plan revision 必须为正数");
            }
            status = requireText(status, "executor plan status");
            goal = requireText(goal, "executor plan goal");
            steps = List.copyOf(Objects.requireNonNull(steps, "executor plan steps 不能为空"));
        }
    }

    /** ExecutorPlanStep 的用户安全投影；结果只暴露执行者显式上报的结果引用或失败码。 */
    public record ExecutorPlanStepDetails(
            int ordinal, String title, String status, String result, String failure) {
        public ExecutorPlanStepDetails {
            if (ordinal < 1) {
                throw new IllegalArgumentException("executor step ordinal 必须为正数");
            }
            title = requireText(title, "executor step title");
            status = requireText(status, "executor step status");
            result = normalize(result);
            failure = normalize(failure);
        }
    }

    public record ExecutionDetails(
            String executionId,
            String nodeId,
            String scope,
            int attemptNo,
            String status,
            String ownerKind,
            int consecutiveFailures,
            DispatchDetails latestDispatch,
            Instant createdAt,
            Instant updatedAt) {
        public ExecutionDetails {
            executionId = requireText(executionId, "executionId");
            scope = requireText(scope, "execution scope");
            status = requireText(status, "execution status");
            ownerKind = requireText(ownerKind, "execution ownerKind");
            if (attemptNo < 1 || consecutiveFailures < 0) {
                throw new IllegalArgumentException("execution attempt/failures 不合法");
            }
            Objects.requireNonNull(createdAt, "execution createdAt 不能为空");
            Objects.requireNonNull(updatedAt, "execution updatedAt 不能为空");
        }
    }

    public record DispatchDetails(
            String status, Instant nextRunAt, int deliveryAttempts, Instant updatedAt) {
        public DispatchDetails {
            status = requireText(status, "dispatch status");
            Objects.requireNonNull(nextRunAt, "dispatch nextRunAt 不能为空");
            Objects.requireNonNull(updatedAt, "dispatch updatedAt 不能为空");
            if (deliveryAttempts < 0) {
                throw new IllegalArgumentException("deliveryAttempts 不能为负数");
            }
        }
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String requireText(String value, String field) {
        value = normalize(value);
        if (value == null) {
            throw new IllegalArgumentException(field + " 不能为空白");
        }
        return value;
    }
}
