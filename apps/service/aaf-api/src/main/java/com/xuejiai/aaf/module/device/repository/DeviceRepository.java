package com.xuejiai.aaf.module.device.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.xuejiai.aaf.framework.crud.CrudEntityRepository;
import com.xuejiai.aaf.module.device.domain.Device;

import jakarta.persistence.LockModeType;

/** 设备数据访问层。 */
public interface DeviceRepository extends CrudEntityRepository<Device> {

    Optional<Device> findByDeviceKey(String deviceKey);

    boolean existsBySerialNumberAndStatus(String serialNumber, String status);

    Optional<Device> findFirstByScenarioKeyAndSubjectIdAndStatusOrderByIdAsc(
            String scenarioKey, Long subjectId, String status);

    List<Device> findAllByScenarioKeyAndSubjectIdOrderByIdAsc(String scenarioKey, Long subjectId);

    Optional<Device> findFirstByScenarioKeyAndSubjectIdAndDeviceTypeAndStatusOrderByIdAsc(
            String scenarioKey, Long subjectId, String deviceType, String status);

    List<Device> findByScenarioKeyAndStatusAndOnlineStatus(
            String scenarioKey, String status, String onlineStatus);

    List<Device> findByOnlineStatusIn(Collection<String> onlineStatuses);

    List<Device> findByStatusAndOnlineStatus(String status, String onlineStatus);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Device d where d.id = :id")
    Optional<Device> findLockedById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Device d where d.deviceKey = :deviceKey")
    Optional<Device> findLockedByDeviceKey(@Param("deviceKey") String deviceKey);
}
