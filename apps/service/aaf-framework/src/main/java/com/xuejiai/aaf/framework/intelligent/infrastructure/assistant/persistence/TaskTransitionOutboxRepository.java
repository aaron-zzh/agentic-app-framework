package com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskTransitionOutboxRepository
        extends JpaRepository<TaskTransitionOutboxEntity, Long> {

    Optional<TaskTransitionOutboxEntity> findByOutboxId(String outboxId);

    @Query(
            value =
                    """
                    select *
                    from ai_task_transition_outbox
                    where status = 'PENDING'
                    order by created_at asc
                    limit :limit
                    for update skip locked
                    """,
            nativeQuery = true)
    List<TaskTransitionOutboxEntity> claimPending(@Param("limit") int limit);
}
