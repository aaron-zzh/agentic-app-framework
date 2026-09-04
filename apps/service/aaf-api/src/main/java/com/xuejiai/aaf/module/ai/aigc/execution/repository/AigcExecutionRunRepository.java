package com.xuejiai.aaf.module.ai.aigc.execution.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.xuejiai.aaf.framework.crud.CrudEntityRepository;
import com.xuejiai.aaf.module.ai.aigc.execution.domain.AigcExecutionRun;

import jakarta.persistence.LockModeType;

public interface AigcExecutionRunRepository extends CrudEntityRepository<AigcExecutionRun> {

    List<AigcExecutionRun> findByProjectIdAndActionKeyAndDeletedFalseOrderByIdDesc(
            Long projectId, String actionKey);

    @Query(
            """
            select run.id from AigcExecutionRun run
            where run.projectId = :projectId
              and run.actionKey = 'project.cover.generate'
              and run.status in ('pending', 'running')
              and run.deleted = false
            order by run.id desc
            """)
    List<Long> findActiveProjectCoverRunIds(@Param("projectId") Long projectId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select run from AigcExecutionRun run where run.id = :id")
    Optional<AigcExecutionRun> findLockedById(@Param("id") Long id);

    long countByProjectIdAndStatus(Long projectId, String status);

    List<AigcExecutionRun> findByProjectIdAndStatusIn(Long projectId, List<String> statuses);

    List<AigcExecutionRun> findByProjectIdOrderByIdAsc(Long projectId);

    @Modifying
    @Query(
            value =
                    """
                    UPDATE generation_history
                    SET deleted = true, delete_time = CURRENT_TIMESTAMP
                    WHERE deleted = false
                      AND execution_run_id IN (
                          SELECT id FROM aigc_execution_run WHERE project_id = :projectId
                      )
                    """,
            nativeQuery = true)
    int softDeleteGenerationHistoryByProjectId(@Param("projectId") Long projectId);

    Optional<AigcExecutionRun> findFirstByRetryOfRunIdOrderByRetryCountDesc(Long retryOfRunId);
}
