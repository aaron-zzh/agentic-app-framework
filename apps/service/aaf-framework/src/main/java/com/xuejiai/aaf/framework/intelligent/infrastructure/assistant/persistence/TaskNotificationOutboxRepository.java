package com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskNotificationOutboxRepository
        extends JpaRepository<TaskNotificationOutboxEntity, Long> {

    Optional<TaskNotificationOutboxEntity> findByNotificationId(String notificationId);

    List<TaskNotificationOutboxEntity> findByStatusInOrderByUpdatedAtAsc(
            Collection<String> statuses, Pageable pageable);
}
