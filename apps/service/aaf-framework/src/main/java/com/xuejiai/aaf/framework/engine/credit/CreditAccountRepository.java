package com.xuejiai.aaf.framework.engine.credit;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

/** 积分账户仓储 */
public interface CreditAccountRepository extends JpaRepository<CreditAccount, Long> {

    Optional<CreditAccount> findByUserId(Long userId);

    /** 悲观锁查询，用于余额变更 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM CreditAccount a WHERE a.userId = :userId AND a.deleted = false")
    Optional<CreditAccount> findByUserIdForUpdate(Long userId);
}
