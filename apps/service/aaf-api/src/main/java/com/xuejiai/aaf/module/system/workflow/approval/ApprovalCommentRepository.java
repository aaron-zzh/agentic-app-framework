package com.xuejiai.aaf.module.system.workflow.approval;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 审批评论仓储。
 *
 * @author Kiro
 */
public interface ApprovalCommentRepository extends JpaRepository<ApprovalComment, Long> {

    /** 按流程实例查询评论（按时间升序） */
    List<ApprovalComment> findByProcessInstanceIdOrderByCreateTimeAsc(String processInstanceId);
}
