package com.xuejiai.aaf.framework.intelligent.assistant.hitl;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/** 工具层人工确认仓储（M36）。 */
public interface ToolApprovalRepository extends JpaRepository<ToolApprovalEntity, String> {

    /** 用户待处理审批（按创建时间倒序）。 */
    List<ToolApprovalEntity> findByUserIdAndStatusOrderByCreatedAtDesc(Long userId, String status);

    /** 某作用域内待处理审批——AG-UI confirm 场景只知会话不知 approvalId 时按作用域批量归档。 */
    List<ToolApprovalEntity> findByScopeKeyAndStatus(String scopeKey, String status);
}
