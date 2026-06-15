package com.xuejiai.aaf.module.system.workflow.approval;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 审批表单模板仓储。
 *
 * @author AaronZZH
 */
public interface ApprovalFormTemplateRepository extends JpaRepository<ApprovalFormTemplate, Long> {

    /** 按流程定义 Key 查询模板 */
    Optional<ApprovalFormTemplate> findByProcessKeyAndStatus(String processKey, Integer status);
}
