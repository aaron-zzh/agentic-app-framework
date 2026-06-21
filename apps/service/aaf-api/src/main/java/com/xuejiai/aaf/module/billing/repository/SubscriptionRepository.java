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

    /**
     * 查询即将到期且状态为 ACTIVE 的订阅（用于到期提醒调度器）。
     *
     * <p>条件：status = ACTIVE 且 end_at 不为空且 end_at &lt;= threshold（提前 N 天的窗口边界）。
     */
    List<Subscription> findByStatusAndEndAtIsNotNullAndEndAtLessThanEqual(
            String status, LocalDateTime threshold);
}
