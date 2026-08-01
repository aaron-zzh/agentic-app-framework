package com.xuejiai.aaf.module.approval.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.approval.domain.ApprovalFormTemplate;

/**
 * 审批表单模板仓储。
 *
 * @author AaronZZH
 */
public interface ApprovalFormTemplateRepository extends JpaRepository<ApprovalFormTemplate, Long> {

    /** 按流程定义 Key 查询模板 */
    Optional<ApprovalFormTemplate> findByProcessKeyAndStatus(String processKey, Integer status);
}
