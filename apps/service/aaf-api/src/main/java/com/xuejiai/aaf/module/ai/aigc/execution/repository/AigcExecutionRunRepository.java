package com.xuejiai.aaf.module.ai.aigc.execution.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.xuejiai.aaf.framework.crud.CrudEntityRepository;
import com.xuejiai.aaf.module.ai.aigc.execution.domain.AigcExecutionRun;

public interface AigcExecutionRunRepository extends CrudEntityRepository<AigcExecutionRun> {

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
