package com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence.plan;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

public interface ExecutorPlanRepository extends JpaRepository<ExecutorPlanEntity, Long> {

    Optional<ExecutorPlanEntity> findByPlanId(String planId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from ExecutorPlanEntity p where p.planId = :planId")
    Optional<ExecutorPlanEntity> findForUpdate(String planId);

    @Query(
            """
            select p from ExecutorPlanEntity p
            where p.tenantId = :tenantId and p.taskId = :taskId and p.boardId = :boardId
                and p.status not in ('CANCELLED', 'REJECTED')
            order by p.revision desc
            """)
    List<ExecutorPlanEntity> findActiveCandidates(String tenantId, String taskId, String boardId);
}
