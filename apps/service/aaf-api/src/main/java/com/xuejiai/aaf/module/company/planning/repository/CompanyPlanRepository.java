package com.xuejiai.aaf.module.company.planning.repository;

import java.util.List;

import com.xuejiai.aaf.module.company.planning.domain.CompanyPlan;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyPlanRepository extends JpaRepository<CompanyPlan, Long> {

    List<CompanyPlan> findByStatusAndYear(String status, Integer year);
}
