package com.xuejiai.aaf.module.billing.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.xuejiai.aaf.framework.crud.CrudEntityRepository;
import com.xuejiai.aaf.module.billing.domain.EntitlementQuota;

public interface EntitlementQuotaRepository extends CrudEntityRepository<EntitlementQuota> {

    Optional<EntitlementQuota> findByUserIdAndEntId(Long userId, Long entId);

    /**
     * M4：加行锁读取额度（{@code SELECT ... FOR UPDATE}），供真实扣减使用。
     *
     * <p>{@code check}（只读预判）与 {@code consume}（真扣）分处两个事务，若扣减仍走无锁的读改写， 并发请求会同时读到相同 remain 后各自写回，导致丢失更新甚至
     * remain 变负。与积分侧 {@code CreditAccountRepository#findByUserIdForUpdate} 保持同一并发策略。
     */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query(
            "SELECT q FROM EntitlementQuota q WHERE q.userId = :userId AND q.entId = :entId AND q.deleted = false")
    Optional<EntitlementQuota> findByUserIdAndEntIdForUpdate(
            @org.springframework.data.repository.query.Param("userId") Long userId,
            @org.springframework.data.repository.query.Param("entId") Long entId);

    List<EntitlementQuota> findByUserId(Long userId);

    @org.springframework.data.jpa.repository.Query(
            "SELECT q FROM EntitlementQuota q JOIN EntitlementDef d ON q.entId = d.id "
                    + "WHERE q.userId = :userId AND d.code = :code AND q.deleted = false")
    Optional<EntitlementQuota> findByUserIdAndEntCode(
            @org.springframework.data.repository.query.Param("userId") Long userId,
            @org.springframework.data.repository.query.Param("code") String code);

    List<EntitlementQuota> findByNextResetAtLessThanEqual(LocalDateTime now);
}
