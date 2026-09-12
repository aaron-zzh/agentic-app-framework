package com.xuejiai.aaf.framework.intelligent.assistant.model.plan;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ExecutionId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TaskId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;

/**
 * EXECUTOR 局部执行计划：某一个已分配 executor 在不扩权、不新增兄弟节点的前提下，按可验证步骤完成自己的子任务。
 *
 * <p>与 {@code TaskPlanDraft} 的关系（ADR-006 议题三）：{@code TaskPlanDraft} 回答"由哪些 executor
 * 以什么聚合合同完成根目标"，是父计划；本类回答"某一个 executor 具体怎么做"，是局部执行计划。两者不是竞争的 TaskPlan，L3 持有本聚合的持久状态，L2 Agent 只在当前
 * execution 中读取不可变 {@code revision} 快照并执行。
 *
 * <p><b>不可变 revision（ADR-006 议题一/三推论）</b>：{@code SUBMITTED} 之后正文不可变；需要修订时创建新 revision，不 update
 * 旧正文。这与 {@code ExecutionProfileSnapshot} 的"单次执行单一画像"不变量对应——一次 planning execution 产出一个
 * revision，{@code APPROVED → EXECUTING} 时把该 revision 冻结进执行 execution 的画像，恢复只能复用同 revision。
 *
 * <p><b>状态机</b>：
 *
 * <pre>
 * DRAFT → PLANNING → SUBMITTED → REVIEW_REQUIRED ─→ REJECTED ─→（新 revision 的 DRAFT）
 *                              └────────────────→ APPROVED
 * APPROVED → EXECUTING → COMPLETED
 *                      ├→ FAILED
 *                      └→ CANCELLED
 * PLANNING/SUBMITTED/REVIEW_REQUIRED/APPROVED → CANCELLED
 * </pre>
 *
 * <p>只有 {@link ExecutorPlanPort} 的方法能产生状态转换，且全部转换必须 CAS（{@code lock_version}），不允许业务代码直接 修改 {@link
 * #status}。
 */
public record ExecutorPlan(
        String planId,
        TenantId tenantId,
        TaskId taskId,
        String nodeId,
        ExecutionId executionId,
        String executorAgentId,
        int revision,
        Status status,
        String goal,
        Map<String, Object> policySnapshot,
        List<String> risks,
        Map<String, Object> verification,
        String submittedByType,
        String submittedById,
        String reviewedByType,
        String reviewedById,
        String reviewComment,
        Instant approvedAt,
        Instant startedAt,
        Instant finishedAt,
        long lockVersion) {

    /** 计划标识只允许安全字符：与 {@code NodeIdentity.SAFE_NODE_KEY} 同一约束，避免另一套校验规则。 */
    private static final Pattern SAFE_KEY = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._\\-]{0,127}");

    public ExecutorPlan {
        planId = requireSafeKey(planId, "planId");
        Objects.requireNonNull(tenantId, "tenantId 不能为空");
        Objects.requireNonNull(taskId, "taskId 不能为空");
        nodeId = requireSafeKey(nodeId, "nodeId");
        Objects.requireNonNull(executionId, "executionId 不能为空");
        executorAgentId = requireText(executorAgentId, "executorAgentId");
        if (revision < 1) {
            throw new IllegalArgumentException("revision 必须从 1 开始");
        }
        Objects.requireNonNull(status, "status 不能为空");
        goal = requireText(goal, "goal");
        policySnapshot = Map.copyOf(Objects.requireNonNull(policySnapshot, "policySnapshot 不能为空"));
        risks = List.copyOf(Objects.requireNonNull(risks, "risks 不能为空"));
        verification = Map.copyOf(Objects.requireNonNull(verification, "verification 不能为空"));
        if (lockVersion < 0) {
            throw new IllegalArgumentException("lockVersion 不能小于 0");
        }
    }

    /** 是否已进入内容不可变阶段（SUBMITTED 及之后）；DRAFT/PLANNING 仍可整体重建正文。 */
    public boolean contentImmutable() {
        return status != Status.DRAFT && status != Status.PLANNING;
    }

    /** 是否已批准，可供 {@code APPROVED → EXECUTING} 冻结进执行画像。 */
    public boolean approved() {
        return status == Status.APPROVED
                || status == Status.EXECUTING
                || status == Status.COMPLETED
                || status == Status.FAILED;
    }

    /** 是否已进入终态，不再接受任何转换。 */
    public boolean terminal() {
        return status == Status.COMPLETED || status == Status.FAILED || status == Status.CANCELLED;
    }

    private static String requireSafeKey(String value, String field) {
        var text = requireText(value, field);
        if (!SAFE_KEY.matcher(text).matches()) {
            throw new IllegalArgumentException(field + " 必须是安全键（字母数字开头，仅含 A-Za-z0-9._-）: " + text);
        }
        return text;
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " 不能为空");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空白");
        }
        return value.trim();
    }

    /** ADR-006 状态机；转换权只在 {@link ExecutorPlanPort} 实现内，本枚举不提供状态转换方法以避免绕过 CAS。 */
    public enum Status {
        DRAFT,
        PLANNING,
        SUBMITTED,
        REVIEW_REQUIRED,
        APPROVED,
        EXECUTING,
        COMPLETED,
        FAILED,
        CANCELLED,
        REJECTED
    }
}
