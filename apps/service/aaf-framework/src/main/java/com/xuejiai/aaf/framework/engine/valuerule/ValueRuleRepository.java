package com.xuejiai.aaf.framework.engine.valuerule;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ValueRuleRepository extends JpaRepository<ValueRule, Long> {

    @Query(
            """
        SELECT r FROM ValueRule r
        WHERE r.enabled = true AND r.ruleType = 'FORBIDDEN' AND r.deleted = false
        ORDER BY r.priority DESC
        """)
    List<ValueRule> findEnabledForbiddenRules();
}
