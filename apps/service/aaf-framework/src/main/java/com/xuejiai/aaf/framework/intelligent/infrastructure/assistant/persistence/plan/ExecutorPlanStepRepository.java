package com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence.plan;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

public interface ExecutorPlanStepRepository extends JpaRepository<ExecutorPlanStepEntity, Long> {

    List<ExecutorPlanStepEntity> findByPlanIdOrderByOrdinalAsc(String planId);

    Optional<ExecutorPlanStepEntity> findByPlanIdAndStepKey(String planId, String stepKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            "select s from ExecutorPlanStepEntity s where s.planId = :planId and s.stepKey = :stepKey")
    Optional<ExecutorPlanStepEntity> findForUpdate(String planId, String stepKey);

    @Query(
            "select s from ExecutorPlanStepEntity s where s.planId = :planId and s.status = 'RUNNING'")
    List<ExecutorPlanStepEntity> findRunning(String planId);
}
