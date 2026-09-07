package com.xuejiai.aaf.module.ai.assistant.service;

import static com.xuejiai.aaf.common.exception.ExceptionUtil.exception;
import static com.xuejiai.aaf.module.ai.assistant.AssistantErrorCode.DELEGATED_TASK_NOT_FOUND;

import java.util.List;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import com.xuejiai.aaf.framework.intelligent.assistant.port.DelegatedTaskPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.TaskBoardPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.plan.ExecutorPlanPort;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TaskId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.UserId;
import com.xuejiai.aaf.framework.org.OrgContext;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.ai.assistant.vo.ExecutorPlanSummaryVO;
import com.xuejiai.aaf.module.ai.assistant.vo.ExecutorPlanSummaryVO.StepSummary;

/**
 * ExecutorPlan 只读查询服务（AAF-114 #11409 任务摘要组件）。
 *
 * <p>只暴露只读投影，不提供任何写操作——计划状态转换只能经 {@link ExecutorPlanPort} 走既有 CAS 流程， 本服务不绕过该边界。
 */
@Service
public class ExecutorPlanQueryService {

    private final DelegatedTaskPort tasks;
    private final TaskBoardPort boards;
    private final ExecutorPlanPort executorPlans;
    private final OperatorContext operators;

    public ExecutorPlanQueryService(
            DelegatedTaskPort tasks,
            TaskBoardPort boards,
            ExecutorPlanPort executorPlans,
            OperatorContext operators) {
        this.tasks = tasks;
        this.boards = boards;
        this.executorPlans = executorPlans;
        this.operators = operators;
    }

    /**
     * 查询某委托任务下全部子任务节点中当前活跃的 ExecutorPlan 摘要。
     *
     * <p>未标记 {@code requiresPlan} 的节点天然不产生活跃计划（{@link ExecutorPlanPort#findActive}
     * 对其返回空），不需要额外判断节点类型。
     */
    public List<ExecutorPlanSummaryVO> findActivePlans(String taskId) {
        var tenantId = tenantId();
        var task =
                tasks.find(tenantId, new TaskId(taskId))
                        .orElseThrow(() -> exception(DELEGATED_TASK_NOT_FOUND))
                        .task();
        if (!task.userId().equals(userId())) {
            throw new AccessDeniedException("无权访问该委托任务");
        }
        var board = boards.find(tenantId, task.taskId());
        if (board.isEmpty()) {
            return List.of();
        }
        return board.get().subTasks().keySet().stream()
                .map(subTaskId -> executorPlans.findActive(tenantId, task.taskId(), subTaskId))
                .filter(java.util.Optional::isPresent)
                .map(java.util.Optional::get)
                .map(this::toSummary)
                .toList();
    }

    private ExecutorPlanSummaryVO toSummary(
            com.xuejiai.aaf.framework.intelligent.assistant.model.plan.ExecutorPlan plan) {
        var steps =
                executorPlans.findSteps(plan.tenantId(), plan.planId()).stream()
                        .map(
                                step ->
                                        new StepSummary(
                                                step.stepKey(),
                                                step.ordinal(),
                                                step.title(),
                                                step.status().name(),
                                                step.failureCode()))
                        .toList();
        return new ExecutorPlanSummaryVO(
                plan.planId(),
                plan.boardId(),
                plan.revision(),
                plan.status().name(),
                plan.goal(),
                steps);
    }

    private TenantId tenantId() {
        var orgId = OrgContext.getCurrentOrgId();
        if (orgId == null) {
            throw new AccessDeniedException("请求缺少组织上下文");
        }
        return new TenantId(orgId.toString());
    }

    private UserId userId() {
        var value =
                operators.currentOwnerId().orElseThrow(() -> new AccessDeniedException("请求未认证"));
        return new UserId(value.toString());
    }
}
