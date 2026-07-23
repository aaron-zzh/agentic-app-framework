package com.xuejiai.aaf.module.billing.repository;

import java.util.List;
import java.util.Optional;

import com.xuejiai.aaf.framework.crud.CrudEntityRepository;
import com.xuejiai.aaf.module.billing.domain.SubscriptionPlan;

public interface SubscriptionPlanRepository extends CrudEntityRepository<SubscriptionPlan> {

    Optional<SubscriptionPlan> findByCode(String code);

    List<SubscriptionPlan> findByStatusOrderBySortAsc(String status);
}
