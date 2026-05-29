package com.xuejiai.aaf.module.billing.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.billing.domain.PlanEntitlement;

public interface PlanEntitlementRepository extends JpaRepository<PlanEntitlement, Long> {

    List<PlanEntitlement> findByPlanId(Long planId);

    Optional<PlanEntitlement> findByPlanIdAndEntId(Long planId, Long entId);
}
