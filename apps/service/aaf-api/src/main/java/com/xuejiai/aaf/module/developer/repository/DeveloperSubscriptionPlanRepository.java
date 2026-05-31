package com.xuejiai.aaf.module.developer.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.xuejiai.aaf.module.developer.domain.DeveloperSubscriptionPlan;

public interface DeveloperSubscriptionPlanRepository
        extends JpaRepository<DeveloperSubscriptionPlan, Long>,
                JpaSpecificationExecutor<DeveloperSubscriptionPlan> {

    Optional<DeveloperSubscriptionPlan> findByCode(String code);

    List<DeveloperSubscriptionPlan> findByStatusOrderBySortOrderAsc(String status);
}
