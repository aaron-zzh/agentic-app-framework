package com.xuejiai.aaf.module.company.planning.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.company.planning.domain.CompanyPlan;

public interface CompanyPlanRepository extends JpaRepository<CompanyPlan, Long> {

    List<CompanyPlan> findByStatusAndYear(String status, Integer year);
}
