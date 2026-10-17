package com.xuejiai.aaf.module.elder.guard.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.xuejiai.aaf.framework.crud.CrudEntityRepository;
import com.xuejiai.aaf.module.elder.guard.domain.AlertEvent;

import jakarta.persistence.LockModeType;

/** 预警事件数据访问层。 */
public interface AlertEventRepository extends CrudEntityRepository<AlertEvent> {

    Optional<AlertEvent> findByDeviceIdAndEventId(Long deviceId, String eventId);

    List<AlertEvent> findByElderIdOrderByDeviceOccurredAtDescIdDesc(
            Long elderId, Pageable pageable);

    @Query(
            "select a.id from AlertEvent a where a.status = :status and a.nextActionAt is not null "
                    + "and a.nextActionAt <= :now order by a.nextActionAt asc")
    List<Long> findDueIds(@Param("status") String status, @Param("now") LocalDateTime now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AlertEvent a where a.id = :id")
    Optional<AlertEvent> findLockedById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AlertEvent a where a.deviceId = :deviceId and a.eventId = :eventId")
    Optional<AlertEvent> findLockedByDeviceIdAndEventId(
            @Param("deviceId") Long deviceId, @Param("eventId") String eventId);
}
