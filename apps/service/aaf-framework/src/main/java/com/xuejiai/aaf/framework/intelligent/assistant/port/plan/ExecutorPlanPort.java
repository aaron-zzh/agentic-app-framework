package com.xuejiai.aaf.framework.intelligent.assistant.port.plan;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.xuejiai.aaf.framework.intelligent.assistant.model.plan.ExecutorPlan;
import com.xuejiai.aaf.framework.intelligent.assistant.model.plan.ExecutorPlanStep;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ExecutionId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TaskId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;

/**
 * {@link ExecutorPlan} 状态机的唯一写入边界；所有转换必须 CAS（{@code lock_version}），业务代码不得绕过本端口直接改状态。
 *
 * <p>命令与结果类型集中在本接口内（而非散落多个小文件），因为它们只服务于这一组状态转换，符合"不为一次性代码创建过多文件"的简洁原则。
 */
public interface ExecutorPlanPort {

    /** DRAFT：新建一次 planning execution 对应的计划草稿。 */
    ExecutorPlan beginPlanning(BeginPlanningCommand command);

    /** DRAFT/PLANNING → SUBMITTED：内容自此不可变；同批判定是否命中确定性白名单。 */
    ExecutorPlan submit(SubmitPlanCommand command);

    /** REVIEW_REQUIRED → APPROVED/REJECTED：人工审批结果落地；REJECTED 不创建新 revision，由调用方另起 beginPlanning。 */
    ExecutorPlan review(ReviewPlanCommand command);

    /** APPROVED → EXECUTING：冻结 revision，供执行 execution 的画像引用。 */
    ExecutorPlan claimApproved(ClaimApprovedCommand command);

    /** 步骤 PENDING → RUNNING：要求依赖已全部完成且同 plan 当前无其它 RUNNING 步骤。 */
    ExecutorPlanStep startStep(StartStepCommand command);

    /** 步骤 RUNNING → COMPLETED；全部步骤完成后调用方应另调 {@link #complete} 收敛计划终态。 */
    ExecutorPlanStep completeStep(CompleteStepCommand command);

    /** 步骤 RUNNING → FAILED。 */
    ExecutorPlanStep failStep(FailStepCommand command);

    /** EXECUTING → COMPLETED。 */
    ExecutorPlan complete(TenantId tenantId, String planId, long expectedLockVersion, Instant at);

    /** EXECUTING → FAILED。 */
    ExecutorPlan fail(
            TenantId tenantId,
            String planId,
            long expectedLockVersion,
            String failureCode,
            Instant at);

    /** PLANNING/SUBMITTED/REVIEW_REQUIRED/APPROVED/EXECUTING → CANCELLED。 */
    ExecutorPlan cancel(TenantId tenantId, String planId, long expectedLockVersion, Instant at);

    /** 查找某 Task、某 TaskNode 当前活跃的计划（最新非终态 revision）；未要求局部计划的节点返回空。 */
    Optional<ExecutorPlan> findActive(TenantId tenantId, TaskId taskId, String nodeId);

    /** 按节点返回某 Task 的最新计划 revision，包括已完成或失败的历史终态计划。 */
    Map<String, ExecutorPlan> findLatestByTask(TenantId tenantId, TaskId taskId);

    /** 读取某计划的全部步骤，按 ordinal 排序。 */
    List<ExecutorPlanStep> findSteps(TenantId tenantId, String planId);

    record BeginPlanningCommand(
            TenantId tenantId,
            TaskId taskId,
            String nodeId,
            ExecutionId executionId,
            String executorAgentId,
            String goal,
            Map<String, Object> policySnapshot,
            Instant at) {
        public BeginPlanningCommand {
            Objects.requireNonNull(tenantId, "tenantId 不能为空");
            Objects.requireNonNull(taskId, "taskId 不能为空");
            Objects.requireNonNull(executionId, "executionId 不能为空");
            Objects.requireNonNull(at, "at 不能为空");
        }
    }

    record SubmitPlanCommand(
            TenantId tenantId,
            String planId,
            long expectedLockVersion,
            List<String> risks,
            Map<String, Object> verification,
            List<StepDraft> steps,
            String submittedByType,
            String submittedById,
            boolean autoApproved,
            Instant at) {
        public SubmitPlanCommand {
            Objects.requireNonNull(tenantId, "tenantId 不能为空");
            Objects.requireNonNull(at, "at 不能为空");
        }
    }

    /** 提交计划时随附的步骤草稿；持久化后才分配 {@code ExecutorPlanStep} 的完整状态字段。 */
    record StepDraft(
            String stepKey,
            int ordinal,
            String title,
            String instruction,
            List<String> dependencies,
            List<String> requiredTools,
            List<String> completionCriteria) {
        public StepDraft {
            Objects.requireNonNull(stepKey, "stepKey 不能为空");
        }
    }

    record ReviewPlanCommand(
            TenantId tenantId,
            String planId,
            long expectedLockVersion,
            boolean approved,
            String reviewedByType,
            String reviewedById,
            String reviewComment,
            Instant at) {
        public ReviewPlanCommand {
            Objects.requireNonNull(tenantId, "tenantId 不能为空");
            Objects.requireNonNull(at, "at 不能为空");
        }
    }

    record ClaimApprovedCommand(
            TenantId tenantId, String planId, long expectedLockVersion, Instant at) {
        public ClaimApprovedCommand {
            Objects.requireNonNull(tenantId, "tenantId 不能为空");
            Objects.requireNonNull(at, "at 不能为空");
        }
    }

    record StartStepCommand(
            TenantId tenantId,
            String planId,
            String stepKey,
            long expectedLockVersion,
            Instant at) {
        public StartStepCommand {
            Objects.requireNonNull(tenantId, "tenantId 不能为空");
            Objects.requireNonNull(at, "at 不能为空");
        }
    }

    record CompleteStepCommand(
            TenantId tenantId,
            String planId,
            String stepKey,
            long expectedLockVersion,
            String resultRef,
            Instant at) {
        public CompleteStepCommand {
            Objects.requireNonNull(tenantId, "tenantId 不能为空");
            Objects.requireNonNull(at, "at 不能为空");
        }
    }

    record FailStepCommand(
            TenantId tenantId,
            String planId,
            String stepKey,
            long expectedLockVersion,
            String failureCode,
            Instant at) {
        public FailStepCommand {
            Objects.requireNonNull(tenantId, "tenantId 不能为空");
            Objects.requireNonNull(at, "at 不能为空");
        }
    }

    /** 乐观锁冲突：调用方持有的 {@code expectedLockVersion} 与当前行不一致。 */
    final class StaleExecutorPlanException extends IllegalStateException {
        public StaleExecutorPlanException(String planId) {
            super("计划已被并发修改，lock_version 冲突: " + planId);
        }
    }

    /** 违反状态机不变量的转换请求（如目标状态不可达、依赖未满足、同 plan 已有 RUNNING 步骤）。 */
    final class InvalidPlanTransitionException extends IllegalStateException {
        public InvalidPlanTransitionException(String reason) {
            super(reason);
        }
    }
}
