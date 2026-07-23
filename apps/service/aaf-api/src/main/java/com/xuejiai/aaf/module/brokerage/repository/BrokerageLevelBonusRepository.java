package com.xuejiai.aaf.module.brokerage.repository;

import java.util.Optional;

import com.xuejiai.aaf.framework.crud.CrudEntityRepository;
import com.xuejiai.aaf.module.brokerage.domain.BrokerageLevelBonus;

public interface BrokerageLevelBonusRepository extends CrudEntityRepository<BrokerageLevelBonus> {

    Optional<BrokerageLevelBonus> findByRuleIdAndPlanId(Long ruleId, Long planId);
}
