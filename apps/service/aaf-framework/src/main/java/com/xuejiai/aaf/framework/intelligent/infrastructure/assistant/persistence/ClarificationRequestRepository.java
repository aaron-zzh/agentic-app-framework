package com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

public interface ClarificationRequestRepository
        extends JpaRepository<ClarificationRequestEntity, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            """
            select c from ClarificationRequestEntity c
            where c.tenantId = :tenantId and c.taskId = :taskId and c.status = 'PENDING'
            """)
    Optional<ClarificationRequestEntity> findPendingForUpdate(String tenantId, String taskId);
}
