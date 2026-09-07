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

    /**
     * 按 requestId 精确查询，不加锁（AAF-114 #11408 第二版）。
     *
     * <p>{@code resumeRun} 恢复 Clarification 时只需读取 {@code taskId}/{@code executionId} 构造
     * {@link com.xuejiai.aaf.framework.intelligent.assistant.model.ExecutionInput}，真正的状态转换仍走
     * {@code consumeInputs} 的悲观锁路径——本方法不能加锁，否则与写路径产生不必要的锁竞争。
     */
    @Query(
            """
            select c from ClarificationRequestEntity c
            where c.tenantId = :tenantId and c.requestId = :requestId
            """)
    Optional<ClarificationRequestEntity> findByRequestId(String tenantId, String requestId);
}
