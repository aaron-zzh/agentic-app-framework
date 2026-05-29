package com.xuejiai.aaf.framework.engine.credit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** 积分流水仓储 */
public interface CreditTransactionRepository extends JpaRepository<CreditTransaction, Long> {

    Page<CreditTransaction> findByAccountId(Long accountId, Pageable pageable);
}
