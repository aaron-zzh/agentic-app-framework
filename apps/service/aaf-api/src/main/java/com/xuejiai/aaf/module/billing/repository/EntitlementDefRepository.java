package com.xuejiai.aaf.module.billing.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.billing.domain.EntitlementDef;

public interface EntitlementDefRepository extends JpaRepository<EntitlementDef, Long> {

    Optional<EntitlementDef> findByCode(String code);
}
