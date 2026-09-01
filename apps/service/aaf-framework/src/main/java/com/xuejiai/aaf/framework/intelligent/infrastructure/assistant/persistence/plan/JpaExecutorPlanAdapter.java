package com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence.plan;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.framework.intelligent.assistant.model.plan.ExecutorPlan;
import com.xuejiai.aaf.framework.intelligent.assistant.model.plan.ExecutorPlanStep;
import com.xuejiai.aaf.framework.intelligent.assistant.port.plan.ExecutorPlanPort;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TaskId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;

/**
 * {@link ExecutorPlanPort} 的 PostgreSQL 实现。
 *
 * <p>全部转换先按 {@code planId}/{@code (planId, stepKey)} 悲观加锁读出当前行，比对调用方携带的 {@code
 * expectedLockVersion}，不一致立即 {@link com.xuejiai.aaf.framework.intelligent.assistant.port.plan.ExecutorPlanPort.StaleExecutorPlanException}——这给出比等 JPA
 * flush 时抛 {@code OptimisticLockException} 更早、更明确的失败点，且不依赖 {@code @Version} 自动比对的异常类型。
 */
public class JpaExecutorPlanAdapter implements ExecutorPlanPort {

    private final ExecutorPlanRepository plans;
    private final ExecutorPlanStepRepository steps;

    public JpaExecutorPlanAdapter(ExecutorPlanRepository plans, ExecutorPlanStepRepository steps) {
        this.plans = Objects.requireNonNull(plans, "plans 不能为空");
        this.steps = Objects.requireNonNull(steps, "steps 不能为空");
    }

    @Override
    @Transactional
    public ExecutorPlan beginPlanning(BeginPlanningCommand command) {
        Objects.requireNonNull(command, "command 不能为空");
        // revision 由本层按 (tenant, task, board) 现有最大值 +1 分配，调用方不猜测——避免并发建两次
        // planning 时用同一 revision 号相互覆盖；重复调用仍会在 uk_executor_plan_revision 上失败。
        var nextRevision =
                plans.findActiveCandidates(
                                command.tenantId().value(),
                                command.delegatedTaskId().value(),
                                command.boardId())
                        .stream()
                        .map(ExecutorPlanEntity::getRevision)
                        .max(Integer::compareTo)
                        .map(current -> current + 1)
                        .orElse(1);
        var planId =
                "plan-" + command.delegatedTaskId().value() + "-" + command.boardId() + "-r"
                        + nextRevision;
        if (plans.findByPlanId(planId).isPresent()) {
            throw new IllegalStateException("计划 revision 已存在: " + planId);
        }
        var entity = new ExecutorPlanEntity();
        entity.setPlanId(planId);
        entity.setTenantId(command.tenantId().value());
        entity.setTaskId(command.delegatedTaskId().value());
        entity.setBoardId(command.boardId());
        entity.setExecutorAgentId(command.executorAgentId());
        entity.setRevision(nextRevision);
        entity.setStatus(ExecutorPlan.Status.PLANNING.name());
        entity.setGoal(command.goal());
        entity.setPolicySnapshot(command.policySnapshot());
        entity.setRisks(List.of());
        entity.setVerification(Map.of());
        entity.setCreatedAt(command.at());
        entity.setUpdatedAt(command.at());
        return toDomain(plans.save(entity));
    }

    @Override
    @Transactional
    public ExecutorPlan submit(SubmitPlanCommand command) {
        Objects.requireNonNull(command, "command 不能为空");
        var entity = lockPlan(command.tenantId(), command.planId(), command.expectedLockVersion());
        requireStatus(
                entity,
                Set.of(ExecutorPlan.Status.DRAFT, ExecutorPlan.Status.PLANNING),
                "只能从 DRAFT/PLANNING 提交计划");
        validateStepGraph(command.steps());
        entity.setStatus(
                (command.autoApproved()
                                ? ExecutorPlan.Status.APPROVED
                                : ExecutorPlan.Status.REVIEW_REQUIRED)
                        .name());
        entity.setRisks(command.risks());
        entity.setVerification(command.verification());
        entity.setSubmittedByType(command.submittedByType());
        entity.setSubmittedById(command.submittedById());
        if (command.autoApproved()) {
            entity.setReviewedByType("SYSTEM");
            entity.setReviewedById("auto-approval-whitelist");
            entity.setApprovedAt(command.at());
        }
        entity.setUpdatedAt(command.at());
        for (var draft : command.steps()) {
            var stepEntity = new ExecutorPlanStepEntity();
            stepEntity.setPlanId(command.planId());
            stepEntity.setStepKey(draft.stepKey());
            stepEntity.setOrdinal(draft.ordinal());
            stepEntity.setTitle(draft.title());
            stepEntity.setInstruction(draft.instruction());
            stepEntity.setDependencies(draft.dependencies());
            stepEntity.setRequiredTools(draft.requiredTools());
            stepEntity.setCompletionCriteria(draft.completionCriteria());
            stepEntity.setStatus(ExecutorPlanStep.Status.PENDING.name());
            steps.save(stepEntity);
        }
        return toDomain(plans.save(entity));
    }

    @Override
    @Transactional
    public ExecutorPlan review(ReviewPlanCommand command) {
        Objects.requireNonNull(command, "command 不能为空");
        var entity = lockPlan(command.tenantId(), command.planId(), command.expectedLockVersion());
        requireStatus(
                entity,
                Set.of(ExecutorPlan.Status.REVIEW_REQUIRED),
                "只能在 REVIEW_REQUIRED 状态审批");
        entity.setStatus(
                (command.approved() ? ExecutorPlan.Status.APPROVED : ExecutorPlan.Status.REJECTED)
                        .name());
        entity.setReviewedByType(command.reviewedByType());
        entity.setReviewedById(command.reviewedById());
        entity.setReviewComment(command.reviewComment());
        if (command.approved()) {
            entity.setApprovedAt(command.at());
        } else {
            entity.setFinishedAt(command.at());
        }
        entity.setUpdatedAt(command.at());
        return toDomain(plans.save(entity));
    }

    @Override
    @Transactional
    public ExecutorPlan claimApproved(ClaimApprovedCommand command) {
        Objects.requireNonNull(command, "command 不能为空");
        var entity = lockPlan(command.tenantId(), command.planId(), command.expectedLockVersion());
        requireStatus(entity, Set.of(ExecutorPlan.Status.APPROVED), "只能从 APPROVED 进入 EXECUTING");
        entity.setStatus(ExecutorPlan.Status.EXECUTING.name());
        entity.setStartedAt(command.at());
        entity.setUpdatedAt(command.at());
        return toDomain(plans.save(entity));
    }

    @Override
    @Transactional
    public ExecutorPlanStep startStep(StartStepCommand command) {
        Objects.requireNonNull(command, "command 不能为空");
        var planEntity = requirePlanForStepOp(command.tenantId(), command.planId());
        if (planEntity.getStatus() == null
                || !ExecutorPlan.Status.EXECUTING.name().equals(planEntity.getStatus())) {
            throw new InvalidPlanTransitionException("计划不在 EXECUTING 状态，不能启动步骤");
        }
        if (!steps.findRunning(command.planId()).isEmpty()) {
            throw new InvalidPlanTransitionException("同一计划已有 RUNNING 步骤，不能并行启动");
        }
        var stepEntity =
                steps.findForUpdate(command.planId(), command.stepKey())
                        .orElseThrow(() -> new IllegalArgumentException("步骤不存在: " + command.stepKey()));
        requireStepLockVersion(stepEntity, command.expectedLockVersion());
        requireStepStatus(stepEntity, ExecutorPlanStep.Status.PENDING, "只能从 PENDING 启动步骤");
        var allSteps = steps.findByPlanIdOrderByOrdinalAsc(command.planId());
        var dependencies = readDependencies(stepEntity);
        var unmet =
                dependencies.stream()
                        .filter(
                                depKey ->
                                        allSteps.stream()
                                                .noneMatch(
                                                        other ->
                                                                other.getStepKey().equals(depKey)
                                                                        && ExecutorPlanStep.Status
                                                                                .COMPLETED
                                                                                .name()
                                                                                .equals(
                                                                                        other
                                                                                                .getStatus())))
                        .toList();
        if (!unmet.isEmpty()) {
            throw new InvalidPlanTransitionException("步骤依赖未全部完成: " + unmet);
        }
        stepEntity.setStatus(ExecutorPlanStep.Status.RUNNING.name());
        stepEntity.setStartedAt(command.at());
        return toDomain(steps.save(stepEntity));
    }

    @Override
    @Transactional
    public ExecutorPlanStep completeStep(CompleteStepCommand command) {
        Objects.requireNonNull(command, "command 不能为空");
        requirePlanForStepOp(command.tenantId(), command.planId());
        var stepEntity =
                steps.findForUpdate(command.planId(), command.stepKey())
                        .orElseThrow(() -> new IllegalArgumentException("步骤不存在: " + command.stepKey()));
        requireStepLockVersion(stepEntity, command.expectedLockVersion());
        requireStepStatus(stepEntity, ExecutorPlanStep.Status.RUNNING, "只能完成 RUNNING 步骤");
        stepEntity.setStatus(ExecutorPlanStep.Status.COMPLETED.name());
        stepEntity.setResultRef(command.resultRef());
        stepEntity.setFinishedAt(command.at());
        return toDomain(steps.save(stepEntity));
    }

    @Override
    @Transactional
    public ExecutorPlanStep failStep(FailStepCommand command) {
        Objects.requireNonNull(command, "command 不能为空");
        requirePlanForStepOp(command.tenantId(), command.planId());
        var stepEntity =
                steps.findForUpdate(command.planId(), command.stepKey())
                        .orElseThrow(() -> new IllegalArgumentException("步骤不存在: " + command.stepKey()));
        requireStepLockVersion(stepEntity, command.expectedLockVersion());
        requireStepStatus(stepEntity, ExecutorPlanStep.Status.RUNNING, "只能标记 RUNNING 步骤失败");
        stepEntity.setStatus(ExecutorPlanStep.Status.FAILED.name());
        stepEntity.setFailureCode(command.failureCode());
        stepEntity.setFinishedAt(command.at());
        return toDomain(steps.save(stepEntity));
    }

    @Override
    @Transactional
    public ExecutorPlan complete(
            TenantId tenantId, String planId, long expectedLockVersion, Instant at) {
        var entity = lockPlan(tenantId, planId, expectedLockVersion);
        requireStatus(entity, Set.of(ExecutorPlan.Status.EXECUTING), "只能从 EXECUTING 完成计划");
        entity.setStatus(ExecutorPlan.Status.COMPLETED.name());
        entity.setFinishedAt(at);
        entity.setUpdatedAt(at);
        return toDomain(plans.save(entity));
    }

    @Override
    @Transactional
    public ExecutorPlan fail(
            TenantId tenantId,
            String planId,
            long expectedLockVersion,
            String failureCode,
            Instant at) {
        var entity = lockPlan(tenantId, planId, expectedLockVersion);
        requireStatus(entity, Set.of(ExecutorPlan.Status.EXECUTING), "只能从 EXECUTING 标记失败");
        entity.setStatus(ExecutorPlan.Status.FAILED.name());
        entity.setReviewComment(failureCode);
        entity.setFinishedAt(at);
        entity.setUpdatedAt(at);
        return toDomain(plans.save(entity));
    }

    @Override
    @Transactional
    public ExecutorPlan cancel(
            TenantId tenantId, String planId, long expectedLockVersion, Instant at) {
        var entity = lockPlan(tenantId, planId, expectedLockVersion);
        requireStatus(
                entity,
                Set.of(
                        ExecutorPlan.Status.PLANNING,
                        ExecutorPlan.Status.SUBMITTED,
                        ExecutorPlan.Status.REVIEW_REQUIRED,
                        ExecutorPlan.Status.APPROVED,
                        ExecutorPlan.Status.EXECUTING),
                "当前状态不可取消");
        entity.setStatus(ExecutorPlan.Status.CANCELLED.name());
        entity.setFinishedAt(at);
        entity.setUpdatedAt(at);
        return toDomain(plans.save(entity));
    }

    @Override
    public Optional<ExecutorPlan> findActive(TenantId tenantId, TaskId delegatedTaskId, String boardId) {
        Objects.requireNonNull(tenantId, "tenantId 不能为空");
        Objects.requireNonNull(delegatedTaskId, "delegatedTaskId 不能为空");
        Objects.requireNonNull(boardId, "boardId 不能为空");
        return plans.findActiveCandidates(tenantId.value(), delegatedTaskId.value(), boardId)
                .stream()
                .findFirst()
                .map(JpaExecutorPlanAdapter::toDomain);
    }

    @Override
    public List<ExecutorPlanStep> findSteps(TenantId tenantId, String planId) {
        Objects.requireNonNull(tenantId, "tenantId 不能为空");
        requirePlanBelongsToTenant(tenantId, planId);
        return steps.findByPlanIdOrderByOrdinalAsc(planId).stream()
                .map(JpaExecutorPlanAdapter::toDomain)
                .toList();
    }

    private ExecutorPlanEntity lockPlan(
            TenantId tenantId, String planId, long expectedLockVersion) {
        Objects.requireNonNull(tenantId, "tenantId 不能为空");
        var entity =
                plans.findForUpdate(planId)
                        .orElseThrow(() -> new IllegalArgumentException("计划不存在: " + planId));
        if (!entity.getTenantId().equals(tenantId.value())) {
            throw new IllegalArgumentException("计划不属于当前租户: " + planId);
        }
        if (!entity.getLockVersion().equals(expectedLockVersion)) {
            throw new StaleExecutorPlanException(planId);
        }
        return entity;
    }

    private ExecutorPlanEntity requirePlanForStepOp(TenantId tenantId, String planId) {
        var entity =
                plans.findByPlanId(planId)
                        .orElseThrow(() -> new IllegalArgumentException("计划不存在: " + planId));
        if (!entity.getTenantId().equals(tenantId.value())) {
            throw new IllegalArgumentException("计划不属于当前租户: " + planId);
        }
        return entity;
    }

    private void requirePlanBelongsToTenant(TenantId tenantId, String planId) {
        var entity =
                plans.findByPlanId(planId)
                        .orElseThrow(() -> new IllegalArgumentException("计划不存在: " + planId));
        if (!entity.getTenantId().equals(tenantId.value())) {
            throw new IllegalArgumentException("计划不属于当前租户: " + planId);
        }
    }

    private static void requireStatus(
            ExecutorPlanEntity entity, Set<ExecutorPlan.Status> allowed, String reason) {
        var current = ExecutorPlan.Status.valueOf(entity.getStatus());
        if (!allowed.contains(current)) {
            throw new InvalidPlanTransitionException(reason + "，当前状态: " + current);
        }
    }

    private static void requireStepLockVersion(
            ExecutorPlanStepEntity entity, long expectedLockVersion) {
        if (!entity.getLockVersion().equals(expectedLockVersion)) {
            throw new StaleExecutorPlanException(entity.getPlanId() + "/" + entity.getStepKey());
        }
    }

    private static void requireStepStatus(
            ExecutorPlanStepEntity entity, ExecutorPlanStep.Status expected, String reason) {
        var current = ExecutorPlanStep.Status.valueOf(entity.getStatus());
        if (current != expected) {
            throw new InvalidPlanTransitionException(reason + "，当前状态: " + current);
        }
    }

    /** 校验步骤依赖只引用同一 plan 内已声明的 stepKey，避免悬空依赖导致步骤永久不可启动。 */
    private static void validateStepGraph(List<StepDraft> drafts) {
        var declaredKeys = new LinkedHashSet<String>();
        for (var draft : drafts) {
            if (!declaredKeys.add(draft.stepKey())) {
                throw new IllegalArgumentException("计划不能重复声明同一 stepKey: " + draft.stepKey());
            }
        }
        for (var draft : drafts) {
            for (var dependency : draft.dependencies()) {
                if (!declaredKeys.contains(dependency)) {
                    throw new IllegalArgumentException(
                            "步骤依赖引用了未声明的 stepKey: " + dependency);
                }
            }
        }
    }

    private static List<String> readDependencies(ExecutorPlanStepEntity entity) {
        return entity.getDependencies() == null ? List.of() : List.copyOf(entity.getDependencies());
    }

    private static ExecutorPlan toDomain(ExecutorPlanEntity entity) {
        return new ExecutorPlan(
                entity.getPlanId(),
                new TenantId(entity.getTenantId()),
                new TaskId(entity.getTaskId()),
                entity.getBoardId(),
                entity.getExecutorAgentId(),
                entity.getRevision(),
                ExecutorPlan.Status.valueOf(entity.getStatus()),
                entity.getGoal(),
                entity.getPolicySnapshot() == null ? Map.of() : entity.getPolicySnapshot(),
                entity.getRisks() == null ? List.of() : entity.getRisks(),
                entity.getVerification() == null ? Map.of() : entity.getVerification(),
                entity.getSubmittedByType(),
                entity.getSubmittedById(),
                entity.getReviewedByType(),
                entity.getReviewedById(),
                entity.getReviewComment(),
                entity.getApprovedAt(),
                entity.getStartedAt(),
                entity.getFinishedAt(),
                entity.getLockVersion());
    }

    private static ExecutorPlanStep toDomain(ExecutorPlanStepEntity entity) {
        return new ExecutorPlanStep(
                entity.getPlanId(),
                entity.getStepKey(),
                entity.getOrdinal(),
                entity.getTitle(),
                entity.getInstruction(),
                entity.getDependencies() == null ? List.of() : entity.getDependencies(),
                entity.getRequiredTools() == null ? List.of() : entity.getRequiredTools(),
                entity.getCompletionCriteria() == null
                        ? List.of()
                        : entity.getCompletionCriteria(),
                ExecutorPlanStep.Status.valueOf(entity.getStatus()),
                entity.getResultRef(),
                entity.getFailureCode(),
                entity.getStartedAt(),
                entity.getFinishedAt(),
                entity.getLockVersion());
    }
}
