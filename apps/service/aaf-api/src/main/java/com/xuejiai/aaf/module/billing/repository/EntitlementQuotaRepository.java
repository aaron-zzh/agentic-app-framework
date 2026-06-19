package com.xuejiai.aaf.module.billing.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.billing.domain.EntitlementQuota;

public interface EntitlementQuotaRepository extends JpaRepository<EntitlementQuota, Long> {

    Optional<EntitlementQuota> findByUserIdAndEntId(Long userId, Long entId);

    List<EntitlementQuota> findByUserId(Long userId);

    @org.springframework.data.jpa.repository.Query(
            "SELECT q FROM EntitlementQuota q JOIN EntitlementDef d ON q.entId = d.id "
                    + "WHERE q.userId = :userId AND d.code = :code AND q.deleted = false")
    Optional<EntitlementQuota> findByUserIdAndEntCode(
            @org.springframework.data.repository.query.Param("userId") Long userId,
            @org.springframework.data.repository.query.Param("code") String code);

    /** 查找需要重置的额度（next_reset_at <= 当前时间） */
    List<EntitlementQuota> findByNextResetAtLessThanEqual(LocalDateTime now);
}
