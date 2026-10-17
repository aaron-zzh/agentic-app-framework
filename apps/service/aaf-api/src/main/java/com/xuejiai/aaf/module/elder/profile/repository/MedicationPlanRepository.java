package com.xuejiai.aaf.module.elder.profile.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.xuejiai.aaf.framework.crud.CrudEntityRepository;
import com.xuejiai.aaf.module.elder.profile.domain.MedicationPlan;

/** 用药计划数据访问层。 */
public interface MedicationPlanRepository extends CrudEntityRepository<MedicationPlan> {

    List<MedicationPlan> findAllByElderIdOrderByIdDesc(Long elderId);

    @Query(
            """
            SELECT plan
            FROM MedicationPlan plan
            WHERE plan.status = :status
              AND plan.startDate <= :endDate
              AND (plan.endDate IS NULL OR plan.endDate >= :startDate)
            ORDER BY plan.id ASC
            """)
    List<MedicationPlan> findActiveInDateRange(
            @Param("status") String status,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);
}
