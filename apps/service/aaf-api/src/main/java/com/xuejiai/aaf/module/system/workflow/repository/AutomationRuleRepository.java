package com.xuejiai.aaf.module.system.workflow.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.xuejiai.aaf.module.system.workflow.domain.AutomationRule;

/**
 * 自动化规则仓储。
 *
 * @author AaronZZH & Kiro
 */
public interface AutomationRuleRepository
        extends JpaRepository<AutomationRule, Long>, JpaSpecificationExecutor<AutomationRule> {

    /** 查询指定实体和触发类型的启用规则 */
    List<AutomationRule> findByEntitySlugAndTriggerTypeAndEnabledTrue(
            String entitySlug, String triggerType);
}
