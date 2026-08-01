package com.xuejiai.aaf.module.approval.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.approval.domain.ApprovalComment;

/**
 * 审批评论仓储。
 *
 * @author AaronZZH
 */
public interface ApprovalCommentRepository extends JpaRepository<ApprovalComment, Long> {

    /** 按流程实例查询评论（按时间升序） */
    List<ApprovalComment> findByProcessInstanceIdOrderByCreateTimeAsc(String processInstanceId);
}
