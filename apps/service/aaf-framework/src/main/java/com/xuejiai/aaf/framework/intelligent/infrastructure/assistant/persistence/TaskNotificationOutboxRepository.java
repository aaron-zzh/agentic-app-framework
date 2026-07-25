package com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence;

import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskNotificationOutboxRepository
        extends JpaRepository<TaskNotificationOutboxEntity, String> {

    List<TaskNotificationOutboxEntity> findByStatusInOrderByUpdatedAtAsc(
            Collection<String> statuses, Pageable pageable);
}
