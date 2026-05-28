package com.xuejiai.aaf.module.system.workflow.approval;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 审批记录仓储。
 *
 * @author Kiro
 */
public interface ApprovalRecordRepository extends JpaRepository<ApprovalRecord, Long> {

    /** 按流程实例查询审批时间线（按操作时间升序） */
    List<ApprovalRecord> findByProcessInstanceIdOrderByOperationTimeAsc(String processInstanceId);

    /** 按任务 ID 查询记录 */
    List<ApprovalRecord> findByTaskId(String taskId);

    /** 查询指定审批人的所有记录 */
    List<ApprovalRecord> findByAssigneeOrderByOperationTimeDesc(String assignee);
}
