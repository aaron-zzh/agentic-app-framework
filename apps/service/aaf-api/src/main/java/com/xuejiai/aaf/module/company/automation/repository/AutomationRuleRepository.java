package com.xuejiai.aaf.module.company.automation.repository;

import java.util.List;

import com.xuejiai.aaf.module.company.automation.domain.AutomationRule;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AutomationRuleRepository extends JpaRepository<AutomationRule, Long> {

    List<AutomationRule> findByEnabledTrueAndTriggerEvent(String triggerEvent);
}
