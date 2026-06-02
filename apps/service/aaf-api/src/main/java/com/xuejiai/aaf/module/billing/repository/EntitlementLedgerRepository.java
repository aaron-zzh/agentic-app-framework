package com.xuejiai.aaf.module.billing.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.xuejiai.aaf.module.billing.domain.EntitlementLedger;

public interface EntitlementLedgerRepository extends JpaRepository<EntitlementLedger, Long> {

    Page<EntitlementLedger> findByQuotaIdOrderByCreatedAtDesc(Long quotaId, Pageable pageable);

    /** 按用户查询（通过 quota 关联） */
    @Query(
            "SELECT l FROM EntitlementLedger l JOIN EntitlementQuota q ON l.quotaId = q.id WHERE q.userId = :userId ORDER BY l.createdAt DESC")
    Page<EntitlementLedger> findByUserId(Long userId, Pageable pageable);

    /** 按用户+时间范围查询 */
    @Query(
            "SELECT l FROM EntitlementLedger l JOIN EntitlementQuota q ON l.quotaId = q.id WHERE q.userId = :userId AND l.createdAt BETWEEN :start AND :end ORDER BY l.createdAt DESC")
    List<EntitlementLedger> findByUserIdAndCreatedAtBetween(
            Long userId, LocalDateTime start, LocalDateTime end);
}
