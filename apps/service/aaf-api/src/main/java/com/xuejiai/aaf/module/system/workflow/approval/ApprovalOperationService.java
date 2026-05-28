package com.xuejiai.aaf.module.system.workflow.approval;

import java.util.Map;

import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 审批操作服务——加签、转签、撤回。
 *
 * @author Kiro
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalOperationService {

    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final HistoryService historyService;

    /**
     * 前加签——在当前审批人之前插入新审批人。
     *
     * @param taskId 当前任务 ID
     * @param assignee 加签审批人
     */
    @Transactional
    public void addSignBefore(String taskId, String assignee) {
        var task = requireTask(taskId);
        // 使用 Flowable 多实例动态加人
        runtimeService.addMultiInstanceExecution(
                task.getTaskDefinitionKey(),
                task.getProcessInstanceId(),
                Map.of("assignee", assignee));
        log.info("前加签完成：taskId={}, assignee={}", taskId, assignee);
    }

    /**
     * 后加签——在当前审批人之后追加新审批人。
     *
     * @param taskId 当前任务 ID
     * @param assignee 加签审批人
     */
    @Transactional
    public void addSignAfter(String taskId, String assignee) {
        var task = requireTask(taskId);
        runtimeService.addMultiInstanceExecution(
                task.getTaskDefinitionKey(),
                task.getProcessInstanceId(),
                Map.of("assignee", assignee));
        log.info("后加签完成：taskId={}, assignee={}", taskId, assignee);
    }

    /**
     * 转签——将任务转交给其他人处理。
     *
     * @param taskId 任务 ID
     * @param targetAssignee 目标审批人
     * @param reason 转签原因
     */
    @Transactional
    public void transferSign(String taskId, String targetAssignee, String reason) {
        var task = requireTask(taskId);
        var originalAssignee = task.getAssignee();
        taskService.setAssignee(taskId, targetAssignee);
        if (reason != null) {
            taskService.addComment(taskId, task.getProcessInstanceId(),
                    "转签：%s → %s，原因：%s".formatted(originalAssignee, targetAssignee, reason));
        }
        log.info("转签完成：taskId={}, {} → {}", taskId, originalAssignee, targetAssignee);
    }

    /**
     * 撤回——发起人撤回流程（检查后续节点是否已处理）。
     *
     * @param processInstanceId 流程实例 ID
     * @param initiator 发起人
     */
    @Transactional
    public void withdraw(String processInstanceId, String initiator) {
        // 校验发起人
        var variables = runtimeService.getVariables(processInstanceId);
        var processInitiator = (String) variables.get("initiator");
        if (!initiator.equals(processInitiator)) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN, "只有发起人可以撤回");
        }

        // 检查是否有已完成的后续任务（如果有则不允许撤回）
        var completedTasks = historyService.createHistoricActivityInstanceQuery()
                .processInstanceId(processInstanceId)
                .activityType("userTask")
                .finished()
                .list();

        // 排除发起人自己的提交节点，如果有其他人已处理则不允许撤回
        boolean hasOtherCompleted = completedTasks.stream()
                .anyMatch(t -> t.getAssignee() != null && !t.getAssignee().equals(initiator));
        if (hasOtherCompleted) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "后续节点已处理，无法撤回");
        }

        runtimeService.deleteProcessInstance(processInstanceId, "发起人撤回");
        log.info("流程撤回：processInstanceId={}, initiator={}", processInstanceId, initiator);
    }

    private Task requireTask(String taskId) {
        var task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            throw new BusinessException(GlobalErrorCode.NOT_FOUND, "任务不存在: " + taskId);
        }
        return task;
    }
}
