package com.xuejiai.aaf.module.approval.service;

import java.time.LocalDateTime;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.framework.engine.bpmn.api.BpmnEngine;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 审批操作服务——加签、转签、撤回、退回、催办。
 *
 * @author AaronZZH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalOperationService {

    private final BpmnEngine bpmnEngine;

    /**
     * 前加签——在当前审批人之前插入新审批人。
     *
     * @param taskId 当前任务 ID
     * @param assignee 加签审批人
     */
    @Transactional
    public void addSignBefore(String taskId, String assignee) {
        var task = requireTask(taskId);
        if (task.assignee() == null || task.assignee().isBlank()) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "当前任务尚未分配审批人");
        }
        bpmnEngine.addMultiInstanceExecution(taskId, Map.of("assignee", task.assignee()));
        bpmnEngine.reassignTask(taskId, assignee);
        bpmnEngine.addTaskComment(taskId, "ADD_SIGN_BEFORE", "前加签：" + assignee);
    }

    /**
     * 后加签——在当前审批人之后追加新审批人。
     *
     * @param taskId 当前任务 ID
     * @param assignee 加签审批人
     */
    @Transactional
    public void addSignAfter(String taskId, String assignee) {
        requireTask(taskId);
        bpmnEngine.addMultiInstanceExecution(taskId, Map.of("assignee", assignee));
        bpmnEngine.addTaskComment(taskId, "ADD_SIGN_AFTER", "后加签：" + assignee);
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
        bpmnEngine.reassignTask(taskId, targetAssignee);
        var message = "转签：%s → %s".formatted(task.assignee(), targetAssignee);
        if (reason != null && !reason.isBlank()) {
            message += "，原因：" + reason;
        }
        bpmnEngine.addTaskComment(taskId, "TRANSFER", message);
        log.info("审批任务转签：taskId={}, {} → {}", taskId, task.assignee(), targetAssignee);
    }

    /**
     * 撤回——发起人撤回流程（检查后续节点是否已处理）。
     *
     * @param processInstanceId 流程实例 ID
     * @param initiator 发起人
     */
    @Transactional
    public void withdraw(String processInstanceId, String initiator) {
        try {
            var variables = bpmnEngine.getProcessVariables(processInstanceId);
            if (!initiator.equals(String.valueOf(variables.get("initiator")))) {
                throw new IllegalArgumentException("只有发起人可以撤回");
            }
            var hasOtherCompleted =
                    bpmnEngine.getHistory(processInstanceId).stream()
                            .anyMatch(
                                    record ->
                                            record.assignee() != null
                                                    && !record.assignee().equals(initiator));
            if (hasOtherCompleted) {
                throw new IllegalStateException("后续节点已处理，无法撤回");
            }
            bpmnEngine.terminateInstance(processInstanceId, "发起人撤回");
            log.info("审批流程撤回：processInstanceId={}, initiator={}", processInstanceId, initiator);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN, e.getMessage());
        } catch (IllegalStateException e) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, e.getMessage());
        }
    }

    /** 退回到上一个已完成的审批任务。 */
    @Transactional
    public void returnTask(String taskId, String reason) {
        requireTask(taskId);
        var message = reason == null || reason.isBlank() ? "退回" : "退回原因：" + reason;
        bpmnEngine.addTaskComment(taskId, "RETURN", message);
        bpmnEngine.moveTaskToPreviousCompleted(taskId);
    }

    /** 记录审批催办。 */
    @Transactional
    public void urgeTask(String taskId, String urgerId) {
        requireTask(taskId);
        var message = "催办人：%s，时间：%s".formatted(urgerId, LocalDateTime.now());
        bpmnEngine.addTaskComment(taskId, "URGE", message);
        log.info("审批任务催办：taskId={}，催办人={}", taskId, urgerId);
    }

    private BpmnEngine.TaskInfo requireTask(String taskId) {
        var task = bpmnEngine.getTask(taskId);
        if (task == null) {
            throw new BusinessException(GlobalErrorCode.NOT_FOUND, "任务不存在");
        }
        return task;
    }
}
