package com.xuejiai.aaf.module.billing.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.xuejiai.aaf.framework.crud.CrudEntityRepository;
import com.xuejiai.aaf.module.billing.domain.Subscription;

public interface SubscriptionRepository extends CrudEntityRepository<Subscription> {

    Optional<Subscription> findByUserIdAndStatus(Long userId, String status);

    List<Subscription> findByStatusAndEndAtBefore(String status, LocalDateTime time);

    List<Subscription> findByStatusAndLastCreditIssuedAtBeforeOrLastCreditIssuedAtIsNull(
            String status, LocalDateTime threshold);

    List<Subscription> findByStatusAndEndAtIsNotNullAndEndAtLessThanEqual(
            String status, LocalDateTime threshold);
}
