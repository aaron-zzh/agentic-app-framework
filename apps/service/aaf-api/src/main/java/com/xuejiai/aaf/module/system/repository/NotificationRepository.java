package com.xuejiai.aaf.module.system.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.xuejiai.aaf.module.system.domain.Notification;

import java.util.List;

/** 通知数据访问层。 */
public interface NotificationRepository
        extends JpaRepository<Notification, Long>, JpaSpecificationExecutor<Notification> {

    long countByUserIdAndIsReadFalse(Long userId);

    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.id IN :ids AND n.userId = :userId")
    int markAsRead(List<Long> ids, Long userId);
}
