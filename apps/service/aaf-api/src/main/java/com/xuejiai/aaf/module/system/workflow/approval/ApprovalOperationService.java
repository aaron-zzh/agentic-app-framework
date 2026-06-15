package com.xuejiai.aaf.module.system.workflow.approval;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.framework.engine.workflow.WorkflowEngine;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 审批操作服务——加签、转签、撤回。
 *
 * @author AaronZZH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalOperationService {

    private final WorkflowEngine workflowEngine;

    /**
     * 前加签——在当前审批人之前插入新审批人。
     *
     * @param taskId 当前任务 ID
     * @param assignee 加签审批人
     */
    @Transactional
    public void addSignBefore(String taskId, String assignee) {
        workflowEngine.addSign(taskId, assignee);
    }

    /**
     * 后加签——在当前审批人之后追加新审批人。
     *
     * @param taskId 当前任务 ID
     * @param assignee 加签审批人
     */
    @Transactional
    public void addSignAfter(String taskId, String assignee) {
        workflowEngine.addSign(taskId, assignee);
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
        workflowEngine.transferSign(taskId, targetAssignee, reason);
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
            workflowEngine.withdraw(processInstanceId, initiator);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN, e.getMessage());
        } catch (IllegalStateException e) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, e.getMessage());
        }
    }
}
