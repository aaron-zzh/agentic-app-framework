package com.xuejiai.aaf.framework.intelligent.infrastructure.trace.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface ExecutionEventRepository extends JpaRepository<ExecutionEventEntity, String> {

    List<ExecutionEventEntity>
            findByTenantIdAndTaskIdAndEventOffsetGreaterThanOrderByEventOffsetAsc(
                    String tenantId, String taskId, long afterEventOffset);

    List<ExecutionEventEntity> findByTenantIdAndExecutionIdAndSequenceGreaterThanOrderBySequenceAsc(
            String tenantId, String executionId, long afterSequence);

    @Query(
            value = "SELECT event_offset FROM ai_task_event WHERE event_id = :eventId",
            nativeQuery = true)
    Long findEventOffsetByEventId(String eventId);

    @Transactional
    @Query(
            value =
                    """
                    INSERT INTO ai_execution_sequence (tenant_id, execution_id, next_sequence)
                    VALUES (:tenantId, :executionId, 2)
                    ON CONFLICT (tenant_id, execution_id)
                    DO UPDATE SET next_sequence = ai_execution_sequence.next_sequence + 1
                    RETURNING next_sequence - 1
                    """,
            nativeQuery = true)
    long allocateSequence(String tenantId, String executionId);
}
