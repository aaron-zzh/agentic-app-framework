package com.xuejiai.aaf.module.billing.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.billing.domain.CreditGrantRule;

public interface CreditGrantRuleRepository extends JpaRepository<CreditGrantRule, Long> {

    Optional<CreditGrantRule> findByCodeAndStatus(String code, String status);
}
