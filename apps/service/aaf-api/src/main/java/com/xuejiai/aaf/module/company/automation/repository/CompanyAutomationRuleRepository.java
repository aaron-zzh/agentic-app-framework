package com.xuejiai.aaf.module.company.automation.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.company.automation.domain.AutomationRule;

public interface CompanyAutomationRuleRepository extends JpaRepository<AutomationRule, Long> {

    List<AutomationRule> findByEnabledTrueAndTriggerEvent(String triggerEvent);
}
