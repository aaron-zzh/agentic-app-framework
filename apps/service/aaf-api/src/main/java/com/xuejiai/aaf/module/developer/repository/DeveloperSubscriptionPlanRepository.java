package com.xuejiai.aaf.module.developer.repository;

import java.util.List;
import java.util.Optional;

import com.xuejiai.aaf.framework.crud.CrudEntityRepository;
import com.xuejiai.aaf.module.developer.domain.DeveloperSubscriptionPlan;

public interface DeveloperSubscriptionPlanRepository
        extends CrudEntityRepository<DeveloperSubscriptionPlan> {

    Optional<DeveloperSubscriptionPlan> findByCode(String code);

    List<DeveloperSubscriptionPlan> findByStatusOrderBySortOrderAsc(String status);
}
