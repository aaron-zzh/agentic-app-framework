package com.xuejiai.aaf.module.billing.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.billing.domain.CreditPackage;

public interface CreditPackageRepository extends JpaRepository<CreditPackage, Long> {

    List<CreditPackage> findByStatusOrderBySortAsc(String status);
}
