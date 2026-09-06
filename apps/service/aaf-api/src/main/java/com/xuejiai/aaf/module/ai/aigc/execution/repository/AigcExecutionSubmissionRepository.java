package com.xuejiai.aaf.module.ai.aigc.execution.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.xuejiai.aaf.module.ai.aigc.execution.domain.AigcExecutionSubmission;

import jakarta.persistence.LockModeType;

public interface AigcExecutionSubmissionRepository
        extends JpaRepository<AigcExecutionSubmission, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AigcExecutionSubmission> findByProjectIdAndIdempotencyKey(
            Long projectId, String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select submission from AigcExecutionSubmission submission where submission.id = :id")
    Optional<AigcExecutionSubmission> findLockedById(@Param("id") Long id);

    @Modifying
    @Query(
            """
            update AigcExecutionSubmission submission
               set submission.status = 'PREPARING',
                   submission.version = submission.version + 1,
                   submission.updateTime = CURRENT_TIMESTAMP
             where submission.id = :id
               and submission.status = 'INTENT_RECORDED'
            """)
    int claimPreparing(@Param("id") Long id);

    List<AigcExecutionSubmission> findByStatusInOrderByIdAsc(Collection<String> statuses);
}
