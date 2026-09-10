package com.xuejiai.aaf.module.billing.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.xuejiai.aaf.framework.crud.CrudEntityRepository;
import com.xuejiai.aaf.module.billing.domain.Subscription;

public interface SubscriptionRepository extends CrudEntityRepository<Subscription> {

    Optional<Subscription> findByUserIdAndStatus(Long userId, String status);

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query(
            "SELECT s FROM Subscription s WHERE s.userId = :userId AND s.status = :status AND s.deleted = false")
    Optional<Subscription> findByUserIdAndStatusForUpdate(
            @Param("userId") Long userId, @Param("status") String status);

    boolean existsBySkuIdOrPendingSkuId(Long skuId, Long pendingSkuId);

    Optional<Subscription> findByUserIdAndSkuIdAndStatus(Long userId, Long skuId, String status);

    List<Subscription> findByStatusAndEndAtBefore(String status, LocalDateTime time);

    List<Subscription> findByStatusAndLastCreditIssuedAtBeforeOrLastCreditIssuedAtIsNull(
            String status, LocalDateTime threshold);

    List<Subscription> findByStatusAndEndAtIsNotNullAndEndAtLessThanEqual(
            String status, LocalDateTime threshold);
}
