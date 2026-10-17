package com.xuejiai.aaf.module.device.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.xuejiai.aaf.framework.crud.CrudEntityRepository;
import com.xuejiai.aaf.module.device.domain.DeviceMessage;

import jakarta.persistence.LockModeType;

/** 设备消息数据访问层。 */
public interface DeviceMessageRepository extends CrudEntityRepository<DeviceMessage> {

    Optional<DeviceMessage> findByCommandId(String commandId);

    Optional<DeviceMessage> findByMessageId(String messageId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from DeviceMessage m where m.commandId = :commandId")
    Optional<DeviceMessage> findLockedByCommandId(@Param("commandId") String commandId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from DeviceMessage m where m.messageId = :messageId")
    Optional<DeviceMessage> findLockedByMessageId(@Param("messageId") String messageId);

    List<DeviceMessage> findTop100ByStatusInOrderByIssuedAtAsc(Collection<String> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<DeviceMessage> findTop100ByStatusInAndExpiresAtBeforeOrderByExpiresAtAsc(
            Collection<String> statuses, LocalDateTime expiresAt);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<DeviceMessage> findTop100ByStatusAndDispatchedAtBeforeOrderByDispatchedAtAsc(
            String status, LocalDateTime dispatchedAt);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<DeviceMessage>
            findTop100ByCapabilityAndCorrelationIdStartingWithAndExpiresAtBeforeAndStatusInAndResultIsNullOrderByExpiresAtAsc(
                    String capability,
                    String correlationIdPrefix,
                    LocalDateTime expiresAt,
                    Collection<String> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<DeviceMessage> findTop100ByCorrelationIdStartingWithAndStatusInOrderByIssuedAtAsc(
            String correlationIdPrefix, Collection<String> statuses);

    @Query(
            "select coalesce(max(m.sequenceNo), 0) from DeviceMessage m where m.deviceId = :deviceId")
    long findMaxSequenceByDeviceId(@Param("deviceId") Long deviceId);
}
