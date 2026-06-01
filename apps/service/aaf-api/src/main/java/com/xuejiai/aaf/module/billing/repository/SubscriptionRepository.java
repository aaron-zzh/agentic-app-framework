package com.xuejiai.aaf.module.billing.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.billing.domain.Subscription;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    Optional<Subscription> findByUserIdAndStatus(Long userId, String status);

    List<Subscription> findByStatusAndEndAtBefore(String status, LocalDateTime time);

    /** 查询需要发放月度积分的订阅：从未发放过，或上次发放时间早于 threshold */
    List<Subscription> findByStatusAndLastCreditIssuedAtBeforeOrLastCreditIssuedAtIsNull(
            String status, LocalDateTime threshold);
}
