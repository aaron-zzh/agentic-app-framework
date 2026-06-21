package com.xuejiai.aaf.module.brokerage.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.xuejiai.aaf.module.brokerage.domain.BrokerageLevelBonus;

public interface BrokerageLevelBonusRepository
        extends JpaRepository<BrokerageLevelBonus, Long>,
                JpaSpecificationExecutor<BrokerageLevelBonus> {

    Optional<BrokerageLevelBonus> findByRuleIdAndPlanId(Long ruleId, Long planId);
}
