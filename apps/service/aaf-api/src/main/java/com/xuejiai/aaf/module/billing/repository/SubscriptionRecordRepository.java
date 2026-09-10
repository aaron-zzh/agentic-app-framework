package com.xuejiai.aaf.module.billing.repository;

import java.util.List;
import java.util.Optional;

import com.xuejiai.aaf.module.billing.domain.SubscriptionRecord;

public interface SubscriptionRecordRepository
        extends com.xuejiai.aaf.framework.crud.CrudEntityRepository<SubscriptionRecord> {

    Optional<SubscriptionRecord> findByPayOrderId(Long payOrderId);

    @org.springframework.data.jpa.repository.Lock(
            jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query(
            "SELECT r FROM SubscriptionRecord r WHERE r.id = :id AND r.deleted = false")
    Optional<SubscriptionRecord> findByIdForUpdate(
            @org.springframework.data.repository.query.Param("id") Long id);

    @org.springframework.data.jpa.repository.Lock(
            jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query(
            "SELECT r FROM SubscriptionRecord r WHERE r.userId = :userId AND r.fulfillmentStatus = 'PENDING' AND r.deleted = false ORDER BY r.id")
    List<SubscriptionRecord> findLiveByUserIdForUpdate(
            @org.springframework.data.repository.query.Param("userId") Long userId);

    @org.springframework.data.jpa.repository.Lock(
            jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query(
            "SELECT r FROM SubscriptionRecord r WHERE r.userId = :userId AND r.serviceGenerationId = :generationId AND r.payStatus = 'PAID' AND r.fulfillmentStatus = 'FULFILLED' AND r.valueStatus = 'AVAILABLE' AND r.serviceEndAt > :now AND r.deleted = false ORDER BY r.id")
    List<SubscriptionRecord> findAvailableValueSegmentsForUpdate(
            @org.springframework.data.repository.query.Param("userId") Long userId,
            @org.springframework.data.repository.query.Param("generationId") Long generationId,
            @org.springframework.data.repository.query.Param("now") java.time.LocalDateTime now);

    boolean existsBySkuId(Long skuId);
}
