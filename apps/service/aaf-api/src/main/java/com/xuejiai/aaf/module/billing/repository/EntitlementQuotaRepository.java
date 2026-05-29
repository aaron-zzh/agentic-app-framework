package com.xuejiai.aaf.module.billing.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.billing.domain.EntitlementQuota;

public interface EntitlementQuotaRepository extends JpaRepository<EntitlementQuota, Long> {

    Optional<EntitlementQuota> findByUserIdAndEntId(Long userId, Long entId);

    List<EntitlementQuota> findByUserId(Long userId);

    /** 查找需要重置的额度（next_reset_at <= 当前时间） */
    List<EntitlementQuota> findByNextResetAtLessThanEqual(LocalDateTime now);
}
